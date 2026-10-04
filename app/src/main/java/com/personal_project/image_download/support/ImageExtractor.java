package com.personal_project.image_download.support;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.LinkedHashSet;
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

    private static final int MIN_SIZE = 48;   // 이보다 작게 표시되는 이미지는 아이콘/추적 픽셀로 간주
    private static final Pattern JUNK_URL = Pattern.compile(
            "(1x1|spacer|blank\\.|pixel|transparent\\.|/ads?/|doubleclick|googleads|analytics|beacon)", Pattern.CASE_INSENSITIVE);
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

    /** doc 은 baseUrl 기준으로 파싱된 문서여야 한다. */
    public static Set<String> extract(Document doc, String baseUrl) {
        Set<String> result = new LinkedHashSet<String>();
        if (doc == null) return result;
        if (baseUrl != null && !baseUrl.isEmpty()) {
            doc.setBaseUri(baseUrl);
        }

        // img 하나당 "가장 큰 원본" 하나만 선택 (srcset 의 모든 크기를 중복으로 나열하지 않음)
        for (Element img : doc.select("img")) {
            if (isTiny(img)) continue;
            // 큰 원본으로 연결된 썸네일이면 원본만 사용
            Element parent = img.parent();
            if (parent != null && parent.tagName().equals("a") && IMAGE_EXT.matcher(parent.attr("href")).find()) {
                add(result, parent, parent.attr("href"));
                continue;
            }
            // picture 안의 img 는 아래 picture 처리에서 가장 큰 후보로 대체
            if (parent != null && parent.tagName().equals("picture") && !parent.select("source[srcset]").isEmpty()) continue;
            String best = bestFromSrcSet(img.attr("srcset"));
            if (best == null) best = bestFromSrcSet(img.attr("data-srcset"));
            if (best == null) {
                // 지연 로딩 속성이 있으면 src(보통 placeholder/썸네일)보다 우선
                for (int i = LAZY_ATTRS.length - 1; i >= 0 && best == null; i--) {
                    String v = img.attr(LAZY_ATTRS[i]).trim();
                    if (!v.isEmpty() && !v.startsWith("data:")) best = v;
                }
            }
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

        for (Element meta : doc.select("meta[property=og:image], meta[property=og:image:url], meta[name=twitter:image], meta[itemprop=image]")) {
            add(result, meta, meta.attr("content"));
        }
        for (Element link : doc.select("link[rel~=(?i)image_src|apple-touch-icon]")) {
            add(result, link, link.attr("href"));
        }

        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            if (IMAGE_EXT.matcher(href).find()) {
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

    /** srcset 에서 w/x 값이 가장 큰 후보의 원본 URL 을 반환. */
    private static String bestFromSrcSet(String srcset) {
        if (srcset == null || srcset.trim().isEmpty()) return null;
        String best = null;
        double bestScore = -1;
        for (String part : srcset.split(",(?=\\s|$)|,\\s+")) {
            String[] tok = part.trim().split("\\s+");
            if (tok[0].isEmpty()) continue;
            double score = 1;
            if (tok.length > 1) {
                try {
                    score = Double.parseDouble(tok[1].replaceAll("[^0-9.]", ""));
                } catch (NumberFormatException ignored) {
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = tok[0];
            }
        }
        return best;
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
        // 용량이 큰 data URI 는 목록에서 제외 (다운로드 대상이 아님)
        if (lower.startsWith("data:") || lower.startsWith("javascript:") || lower.startsWith("blob:") || lower.startsWith("about:")) {
            return;
        }
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
            out.add(hash >= 0 ? abs.substring(0, hash) : abs);
        }
    }
}
