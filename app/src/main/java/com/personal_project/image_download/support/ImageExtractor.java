package com.personal_project.image_download.support;

import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 문서에서 가능한 모든 이미지 주소를 찾아 절대 경로로 변환한다.
 * (img src / lazy-load 속성 / srcset / picture source / meta og:image / CSS background-image / 이미지로 연결된 a 태그)
 */
public class ImageExtractor {

    private static final String[] LAZY_ATTRS = {
            "src", "data-src", "data-original", "data-lazy-src", "data-lazy",
            "data-original-src", "data-url", "data-img", "data-image", "data-echo", "data-actualsrc"
    };

    /** 이름에 이런 단어가 들어간 data-* 속성은 보통 원본(확대) 이미지 */
    private static final Pattern HIRES_ATTR = Pattern.compile("zoom|large|full|orig|hires|hi-res|big|hd|max", Pattern.CASE_INSENSITIVE);
    /** img 가 아닌 요소에서 이미지 주소를 담는 data-* 속성 이름 */
    private static final Pattern IMAGE_ATTR = Pattern.compile("bg|background|image|img|src|original|poster|photo|zoom|full|large|thumb", Pattern.CASE_INSENSITIVE);
    /** 이보다 짧은 data: 이미지는 placeholder 로 간주 */
    private static final int MIN_DATA_URI = 2000;

    private static final int MIN_SIZE = 48;   // 이보다 작게 표시되는 이미지는 아이콘/추적 픽셀로 간주
    private static final Pattern JUNK_URL = Pattern.compile(
            "(1x1|spacer|blank\\.|pixel|transparent\\.|/ads?/|doubleclick|googleads|analytics|beacon)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSONLD_IMAGE = Pattern.compile(
            "\"(?:image|thumbnailUrl|contentUrl)\"\\s*:\\s*(\\[[^\\]]*\\]|\\{[^}]*\\}|\"[^\"]*\")");
    private static final Pattern JSON_URL = Pattern.compile("\"(https?:[^\"]+)\"");
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*['\"]?([^'\")]+?)['\"]?\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE_EXT = Pattern.compile(
            "\\.(jpe?g|png|gif|webp|bmp|svg|avif|tiff?|ico)(\\?.*|#.*)?$", Pattern.CASE_INSENSITIVE);

    /** 사용자가 입력한 주소를 정리한다. 스킴이 없으면 https:// 를 붙인다. 유효하지 않으면 null. */
    public static String normalizeUrl(String input) {
        if (input == null) return null;
        String url = input.trim();
        if (url.isEmpty() || url.contains(" ")) return null;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("//")) {
            url = "https:" + url;
        } else if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            if (lower.contains("://")) return null;
            url = "https://" + url;
        }
        try {
            URI uri = new URI(url);
            if (uri.getHost() == null || !uri.getHost().contains(".") && !uri.getHost().equals("localhost")) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        return url;
    }

    /**
     * 1) 페이지 원본 HTML 을 받아서 이미지를 찾는다 (백그라운드 스레드).
     * 주소 자체가 이미지이면 그 주소 하나를 돌려준다.
     */
    public static Set<String> fromUrl(String url, String userAgent) throws IOException {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(url)
                    .timeout(15000)
                    .followRedirects(true)
                    .maxBodySize(0)
                    .get();
            return extract(doc, doc.location());
        } catch (UnsupportedMimeTypeException e) {
            if (e.getMimeType() != null && e.getMimeType().startsWith("image/")) {
                return new LinkedHashSet<String>(Collections.singletonList(e.getUrl()));
            }
            throw e;
        }
    }

    /**
     * 2) WebView 가 JavaScript 실행/스크롤 후 넘겨준 결과에서 이미지를 찾는다.
     * @param frames 같은 도메인 iframe 및 Shadow DOM 의 {주소, HTML}
     * @param extras 브라우저가 계산한 배경 이미지 등 절대 주소 (줄바꿈 구분)
     */
    public static Set<String> fromRendered(String pageUrl, String html, List<String[]> frames, String extras) {
        Set<String> result = extract(Jsoup.parse(html, pageUrl), pageUrl);
        if (frames != null) {
            for (String[] f : frames) {
                result.addAll(extract(Jsoup.parse(f[1], f[0]), f[0]));
            }
        }
        if (extras != null && !extras.isEmpty()) {
            Document ctx = Jsoup.parse("", pageUrl);
            for (String u : extras.split("\n")) {
                add(result, ctx, u);
            }
        }
        return result;
    }

    /** doc 은 baseUrl 기준으로 파싱된 문서여야 한다. */
    public static Set<String> extract(Document doc, String baseUrl) {
        Set<String> result = new LinkedHashSet<String>();
        if (doc == null) return result;
        // <base href> 가 있으면 파서가 이미 반영해 두었으므로 덮어쓰지 않는다
        if (doc.baseUri().isEmpty() && baseUrl != null && !baseUrl.isEmpty()) {
            doc.setBaseUri(baseUrl);
        }

        // img 하나당 "가장 큰 원본" 하나만 선택 (srcset 의 모든 크기를 중복으로 나열하지 않음)
        for (Element img : doc.select("img")) {
            if (isTiny(img)) continue;
            Element parent = img.parent();
            // picture 안의 img 는 아래 picture 처리에서 가장 큰 후보로 대체
            if (parent != null && parent.tagName().equals("picture") && !parent.select("source[srcset]").isEmpty()) continue;
            String best = bestFromSrcSet(img.attr("srcset"));
            if (best == null) best = bestFromSrcSet(img.attr("data-srcset"));
            // srcset 이 없는 썸네일이 이미지 파일로 링크되어 있으면 링크(원본)를 사용.
            // srcset 이 있으면 사이트가 이미 고해상도를 제공하는 것이고, 링크는 보통 뷰어 페이지(예: 위키 File:xxx.jpg)
            if (best == null && parent != null && parent.tagName().equals("a") && isImageLink(parent.attr("href"))) {
                add(result, parent, parent.attr("href"));
                continue;
            }
            // data-zoom-image 처럼 이름으로 보아 원본인 속성이 있으면 최우선
            String hires = null, otherData = null;
            for (Attribute at : img.attributes()) {
                String key = at.getKey().toLowerCase(Locale.ROOT);
                if (!key.startsWith("data-") || !looksLikeImageUrl(at.getValue())) continue;
                if (HIRES_ATTR.matcher(key.substring(5)).find()) hires = at.getValue();
                else if (otherData == null) otherData = at.getValue();
            }
            if (hires != null) best = hires;
            if (best == null) {
                // 지연 로딩 속성이 있으면 src(보통 placeholder/썸네일)보다 우선
                for (int i = LAZY_ATTRS.length - 1; i >= 0 && best == null; i--) {
                    String v = img.attr(LAZY_ATTRS[i]).trim();
                    if (!v.isEmpty() && !v.startsWith("data:")) best = v;
                }
            }
            if (best == null) best = otherData;            // 이름을 모르는 lazy 속성 (data-xxx="a.jpg")
            if (best == null) best = img.attr("src");      // base64 로 박힌 이미지
            add(result, img, best);
        }

        for (Element source : doc.select("picture")) {
            String best = null;
            for (Element s : source.select("source")) {
                String c = bestFromSrcSet(s.attr("srcset"));
                if (c != null) best = c;   // 같은 picture 안에서는 마지막(가장 큰/기본) 후보 하나만
            }
            add(result, source, best);
        }
        for (Element video : doc.select("video[poster]")) {
            add(result, video, video.attr("poster"));
        }

        // div 등에 data-bg="..." 처럼 지연 로딩용으로 들어있는 이미지
        for (Element e : doc.getAllElements()) {
            if (e.tagName().equals("img")) continue;
            for (Attribute at : e.attributes()) {
                String key = at.getKey().toLowerCase(Locale.ROOT);
                if (key.startsWith("data-") && IMAGE_ATTR.matcher(key.substring(5)).find()) {
                    String v = at.getValue();
                    if (key.contains("srcset")) v = bestFromSrcSet(v);
                    if (v != null && looksLikeImageUrl(v)) add(result, e, v);
                }
            }
        }

        // svg 안의 <image href>
        for (Element image : doc.select("svg image")) {
            String href = image.attr("href");
            add(result, image, href.isEmpty() ? image.attr("xlink:href") : href);
        }

        // 구조화 데이터(JSON-LD) 의 대표 이미지 (쇼핑몰/뉴스에서 원본 해상도인 경우가 많음)
        for (Element ld : doc.select("script[type=application/ld+json]")) {
            Matcher m = JSONLD_IMAGE.matcher(ld.data());
            while (m.find()) {
                Matcher u = JSON_URL.matcher(m.group(1));
                while (u.find()) add(result, ld, u.group(1).replace("\\/", "/"));
            }
        }

        for (Element meta : doc.select("meta[property=og:image], meta[property=og:image:url], meta[name=twitter:image], meta[itemprop=image]")) {
            add(result, meta, meta.attr("content"));
        }
        for (Element link : doc.select("link[rel~=(?i)image_src|apple-touch-icon]")) {
            add(result, link, link.attr("href"));
        }

        for (Element a : doc.select("a[href]")) {
            if (!a.select("img").isEmpty()) continue;   // img 를 감싼 링크는 위에서 처리
            String href = a.attr("href");
            if (isImageLink(href)) {
                add(result, a, href);
            }
        }

        // 인라인 style 및 <style> 안의 background-image
        for (Element e : doc.select("[style]")) {
            addCss(result, e, e.attr("style"));
        }
        for (Element style : doc.select("style")) {
            addCss(result, style, style.data());
        }
        return result;
    }

    private static void addCss(Set<String> out, Element ctx, String css) {
        if (css == null || css.isEmpty()) return;
        Matcher m = CSS_URL.matcher(css);
        while (m.find()) {
            String u = m.group(1);
            // 스타일시트 안에서는 이미지 확장자가 있는 것만 (폰트 등 제외)
            if (IMAGE_EXT.matcher(u).find() || u.startsWith("data:image")) {
                add(out, ctx, u);
            }
        }
    }

    /**
     * srcset 에서 w/x 값이 가장 큰 후보의 원본 URL 을 반환.
     * "a.jpg 400w,b.jpg 800w" 처럼 쉼표 뒤 공백이 없어도, URL 안에 쉼표가 있어도(w_400,h_300) 처리한다.
     */
    static String bestFromSrcSet(String srcset) {
        if (srcset == null) return null;
        String best = null;
        double bestScore = -1;
        int i = 0, n = srcset.length();
        while (i < n) {
            while (i < n && (Character.isWhitespace(srcset.charAt(i)) || srcset.charAt(i) == ',')) i++;
            if (i >= n) break;
            int start = i;
            while (i < n && !Character.isWhitespace(srcset.charAt(i))) i++;
            String url = srcset.substring(start, i);
            String descriptor = "";
            if (url.endsWith(",")) {
                url = url.replaceAll(",+$", "");
            } else {
                int d = i;
                while (i < n && srcset.charAt(i) != ',') i++;
                descriptor = srcset.substring(d, i).trim();
            }
            if (url.isEmpty()) continue;
            double score = 1;
            String num = descriptor.replaceAll("[^0-9.]", "");
            if (!num.isEmpty()) {
                try {
                    score = Double.parseDouble(num);
                } catch (NumberFormatException ignored) {
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = url;
            }
        }
        return best;
    }

    private static boolean looksLikeImageUrl(String v) {
        if (v == null) return false;
        v = v.trim();
        return v.startsWith("data:image/") ? v.length() >= MIN_DATA_URI : IMAGE_EXT.matcher(v.split("\\s")[0]).find();
    }

    /** 이미지 파일을 직접 가리키는 링크인지. 위키의 "File:Cat.jpg" 같은 뷰어 페이지는 제외. */
    private static boolean isImageLink(String href) {
        if (href == null || !IMAGE_EXT.matcher(href).find()) return false;
        String path = href.split("[?#]")[0];
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(":");
    }

    /** 1x1 추적 픽셀, 작은 아이콘 등 의미 없는 이미지 판별 (width/height 속성 기준). */
    private static boolean isTiny(Element img) {
        int w = px(img.attr("width"));
        int h = px(img.attr("height"));
        return (w > 0 && w <= MIN_SIZE) || (h > 0 && h <= MIN_SIZE);
    }

    private static int px(String v) {
        if (v == null) return -1;
        String d = v.replaceAll("[^0-9]", "");
        if (d.isEmpty() || d.length() > 5 || v.contains("%")) return -1;
        return Integer.parseInt(d);
    }

    private static void add(Set<String> out, Element ctx, String raw) {
        if (raw == null) return;
        String value = raw.trim();
        if (value.isEmpty()) return;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:image/")) {
            // 페이지에 직접 박힌 이미지. 작은 것은 placeholder 이므로 제외
            String compact = value.replaceAll("\\s", "");
            if (compact.length() >= MIN_DATA_URI) out.add(compact);
            return;
        }
        if (lower.startsWith("data:") || lower.startsWith("javascript:") || lower.startsWith("blob:") || lower.startsWith("about:")) {
            return;
        }
        value = value.replace(" ", "%20");
        String abs = ctx.absUrl(value);
        if (abs == null || abs.isEmpty()) {
            // absUrl 이 실패한 경우 (base uri 없음) 직접 해석
            try {
                String base = ctx.baseUri();
                abs = base == null || base.isEmpty() ? value : new URI(base).resolve(value.replace(" ", "%20")).toString();
            } catch (Exception e) {
                return;
            }
        }
        if ((abs.startsWith("http://") || abs.startsWith("https://")) && !JUNK_URL.matcher(abs).find()) {
            int hash = abs.indexOf('#');
            if (hash >= 0) abs = abs.substring(0, hash);
            // 루트 위로 올라가는 ../ 는 브라우저처럼 버린다 (http://a.com/../../x.jpg -> http://a.com/x.jpg)
            abs = abs.replaceFirst("^(https?://[^/]+)(/\\.\\.)+(?=/)", "$1");
            out.add(abs);
        }
    }
}
