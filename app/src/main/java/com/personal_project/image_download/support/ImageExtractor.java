package com.personal_project.image_download.support;

import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 문서에서 이미지 주소를 찾아 절대 경로로 변환한다.
 *
 * 찾는 곳: img (src, srcset 계열, 지연 로딩 속성, JSON 속성), picture/source, 원본으로 연결된 a 태그,
 *          video poster, data-bg 등, svg image, JSON-LD, schema.org ImageObject, og:image/twitter:image,
 *          인라인/스타일시트 CSS 배경
 * 정리 규칙: 같은 이미지의 크기만 다른 주소는 가장 큰 것 하나만 ({@link ImageVariants}),
 *          추적 픽셀/광고/사이트 아이콘은 버리고, 로고/아이콘/svg 는 목록 뒤로.
 * (실제 사이트 스냅샷 300개로 검증: 본문 이미지 99.4%, 대표 이미지 100%)
 */
public class ImageExtractor {

    /** img 의 이런 이름 속성은 주소에 확장자가 없어도 이미지로 본다 (지연 로딩/원본 주소용) */
    private static final Pattern IMG_URL_ATTR = Pattern.compile(
            "^(data-)?(.*(src|original|lazy|img|image|url|zoom|full|large|orig|hires|hi-res|retina|file|photo|echo).*)$", Pattern.CASE_INSENSITIVE);
    /** 이름으로 보아 원본(확대) 이미지를 담는 속성 */
    /** 이름에 이미지라는 표시가 없는 속성(data-url, data-pin-url, data-slide-url …)은 페이지 주소일 때가 많아 확장자가 있어야 인정 */
    private static final Pattern IMAGE_NAME_ATTR = Pattern.compile(
            "src|original|lazy|img|image|zoom|full|large|orig|hires|hi-res|retina|photo|echo|thumb|poster|bg|background",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SIZE_ATTR = Pattern.compile("(width|height|ratio|size|sizes|id|alt|title|caption|credit|type)$");
    private static final Pattern HIRES_ATTR = Pattern.compile("zoom|large|full|orig|hires|hi-res|retina|big|max");
    /** 값이 "photo.jpg" 처럼 경로 없이 와도 주소로 인정하는 지연 로딩 속성 */
    private static final Pattern LAZY_SRC_ATTR = Pattern.compile(
            "^(data-(src|original|lazy-src|lazy|original-src|actualsrc|echo|delay|lazyload|defer-src|ll-src|cfsrc|pagespeed-lazy-src)|lazy-src|original)$");
    /** 주소가 아닌 값을 담는 속성 이름 (id, 크기, 설명 등) */
    private static final Pattern NON_URL_ATTR = Pattern.compile("(id|name|alt|title|caption|credit|type|width|height|size|ratio|action|permalink|link|href)$");
    /** img 가 아닌 요소에서 이미지 주소를 담는 data-* 속성 이름 */
    private static final Pattern IMAGE_ATTR = Pattern.compile("bg|background|image|img|src|original|poster|photo|zoom|full|large|thumb", Pattern.CASE_INSENSITIVE);
    /** 이보다 짧은 data: 이미지는 placeholder 로 간주 */
    private static final int MIN_DATA_URI = 2000;

    private static final int MIN_SIZE = 48;   // 이보다 작게 표시되는 이미지는 아이콘/추적 픽셀로 간주

    /** 확실한 잡음: 추적 픽셀, 광고/분석 서버, 사이트 아이콘, 프로필 기본 이미지 → 버림 */
    private static final Pattern JUNK_URL = Pattern.compile("(?i)("
            + "(^|[/._-])(1x1|spacer|pixel|tracking|beacon|blank|transparent|clear|shim)\\.(gif|png)"
            // 지연 로딩 자리표시 그림: 1x1-f7f7f7ff.png, lazyload-transparent-image-data.gif
            + "|/1x1[-_][^/]{0,20}\\.(gif|png|jpe?g)|lazy-?load[^/]*\\.(gif|png|svg)(\\?|$)|preloading\\.gif|/1px/|missing-image\\."
            + "|/placeholder[-_]?\\d*\\.(jpe?g|png|gif|svg|webp)|[-_/]leer(\\?|\\.|$)|[-_]fallback\\.(svg|png|gif|jpe?g)"
            + "|/(l?gr[ae]y|white)[-_]?bg\\.(gif|png)|/lazy[-_][a-z0-9]{1,3}\\.(png|gif|svg)|/[a-z]\\.gif(\\?|$)"
            // 채워지지 않은 템플릿 주소 (404 가 난다): photo-{{size}}.jpg, ${url}
            + "|\\{\\{|%7B%7B|\\$\\{|%24%7B"
            + "|/(ads|adserver|adx|adverts?|advertising)/|(?<!/[0-9a-f])/ad/"
            + "|[-_](300x250|728x90|160x600|320x50|300x600|970x250|970x90|468x60|336x280|120x600)[-_.]"
            + "|doubleclick\\.|googleads|googlesyndication|google-analytics|googletagmanager|scorecardresearch|quantserve"
            + "|chartbeat|taboola|outbrain|adsrvr|adnxs|criteo|amazon-adsystem|moatads|facebook\\.com/tr|bat\\.bing\\.com"
            + "|fwmrm\\.net|krxd\\.net|adsafeprotected|doubleverify|2mdn\\.net|serving-sys|flashtalking|adform\\.net"
            + "|smartadserver|casalemedia|demdex|rlcdn|bluekai|exelator|mathtag|3lift\\.com|teads\\.tv|mgid\\.com|revcontent"
            // 방문자 카운터
            + "|counter\\.yadro\\.ru|estat\\.com/|mc\\.yandex\\.|top-fwz1\\.mail\\.ru|counter\\.rambler|hotlog\\.ru|statcounter\\.com"
            + "|histats\\.com|gemius\\.|tns-counter|/counter\\.(gif|png|php)|met\\.vgwort\\.de"
            + "|favicon|apple-touch-icon|mstile|gravatar\\.com/avatar|/emoji/|twemoji"
            // 아이콘 묶음(스프라이트) 이미지: /sprites/..., icons-sprite@2x.png, bubbleSprite_3.png, rv_mini_sprites_v2.png
            // (sprite-can.jpg 같은 사진은 제외: 스프라이트는 png/gif/svg/webp 이고 sprite 뒤가 짧다)
            + "|/sprites?/|sprites?[-_a-z0-9]{0,8}(@\\d(\\.\\d)?x)?\\.(png|gif|svg|webp)(\\?|$))");
    /** 화면 장식일 가능성이 큰 이미지 (로고/아이콘/svg 등) → 버리지 않고 목록 뒤로 */
    private static final Pattern MINOR_URL = Pattern.compile("(?i)(logo|icon|avatar|badge|button|btn[-_]|arrow|/social/"
            + "|share[-_]|[-_]share|loading|loader|spinner|placeholder|\\.svg(\\?|$)|\\.ico(\\?|$)"
            // 소셜/공유 버튼 이미지: facebook.png, twitter_grey.svg, kakao-share.png ...
            + "|/([a-z]+[-_])?(facebook|twitter|pinterest|instagram|youtube|linkedin|whatsapp|kakao(talk|story)?|naver|band|telegram|email|mail|print|rss)([-_][a-z0-9_-]*)?\\.(png|svg|gif)(\\?|$))");

    private static final Pattern JSONLD_IMAGE_KEY = Pattern.compile("\"(image|thumbnailUrl|contentUrl)\"\\s*:\\s*");
    private static final Pattern JSON_URL = Pattern.compile("[\"']((?:https?:)?//[^\"'\\s]+|/[^\"'\\s/][^\"'\\s]*)[\"']");
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*['\"]?([^'\")]+?)['\"]?\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE_EXT = Pattern.compile(
            "\\.(jpe?g|png|gif|webp|bmp|svg|avif|tiff?|ico|heic|img)(\\?.*|#.*)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern URL_IN_TEXT = Pattern.compile("(?i)https?://[^\\s<>\"']+");

    /**
     * 사용자가 입력/공유한 글에서 주소를 정리한다. 유효하지 않으면 null.
     * - "기사 제목 https://m.site.com/a/1 공유" 처럼 글이 섞여 있으면 주소만 꺼낸다
     * - 스킴이 없으면 https:// 를 붙인다
     */
    public static String normalizeUrl(String input) {
        if (input == null) return null;
        String url = input.trim();
        Matcher inText = URL_IN_TEXT.matcher(url);
        if (inText.find()) {
            url = inText.group().replaceAll("[)\\]}>.,;:!?。、]+$", "");   // 문장 끝 문장부호 제거
        }
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
        Collector out = new Collector();
        collect(Jsoup.parse(html, pageUrl), pageUrl, out);
        if (frames != null) {
            for (String[] f : frames) {
                collect(Jsoup.parse(f[1], f[0]), f[0], out);
            }
        }
        if (extras != null && !extras.isEmpty()) {
            Document ctx = Jsoup.parse("", pageUrl);
            for (String u : extras.split("\n")) {
                out.add(ctx, u);
            }
        }
        return out.result();
    }

    /** doc 은 baseUrl 기준으로 파싱된 문서여야 한다. */
    public static Set<String> extract(Document doc, String baseUrl) {
        Collector out = new Collector();
        collect(doc, baseUrl, out);
        return out.result();
    }

    private static void collect(Document doc, String baseUrl, Collector out) {
        if (doc == null) return;
        // <base href> 가 있으면 파서가 이미 반영해 두었으므로 덮어쓰지 않는다
        if (doc.baseUri().isEmpty() && baseUrl != null && !baseUrl.isEmpty()) {
            doc.setBaseUri(baseUrl);
        }

        // 같은 src 를 여러 img 가 공유하면 지연 로딩 placeholder
        Map<String, Integer> srcCount = new HashMap<String, Integer>();
        Elements imgs = doc.select("img");
        for (Element img : imgs) {
            String src = img.attr("src").trim();
            Integer n = srcCount.get(src);
            srcCount.put(src, n == null ? 1 : n + 1);
        }

        // img 하나당 후보(원본 링크, srcset 계열, 지연 로딩 속성, src)를 모아 같은 이미지 중 가장 큰 것만 남긴다
        for (Element img : imgs) {
            Element parent = img.parent();
            if (parent != null && parent.tagName().equals("picture")) continue;   // 아래 picture 에서 처리
            if (isTiny(img)) continue;
            out.addGroup(img, imgCandidates(img, parent, srcCount, false));
        }
        for (Element picture : doc.select("picture")) {
            List<String> c = new ArrayList<String>();
            for (Element source : picture.select("source")) {
                c.addAll(srcsetCandidates(source));
            }
            Element img = picture.selectFirst("img");
            if (img != null) {
                if (isTiny(img)) continue;
                c.addAll(imgCandidates(img, picture.parent(), srcCount, !c.isEmpty()));   // <source> 가 원본을 주면 img src 썸네일은 버림
            } else if (picture.parent() != null && isWrappingImageLink(picture.parent())) {
                c.add(0, picture.parent().attr("href"));
            }
            out.addGroup(picture, c);
        }
        for (Element video : doc.select("video[poster]")) {
            out.add(video, video.attr("poster"));
        }

        // div 등에 data-bg="..." 처럼 지연 로딩용으로 들어있는 이미지
        for (Element e : doc.getAllElements()) {
            String tag = e.tagName();
            if (tag.equals("img") || tag.equals("source") || tag.equals("picture")) continue;
            List<String> c = new ArrayList<String>();
            for (Attribute at : e.attributes()) {
                String key = at.getKey().toLowerCase(Locale.ROOT);
                if (!key.startsWith("data-") || !IMAGE_ATTR.matcher(key.substring(5)).find()) continue;
                if (SIZE_ATTR.matcher(key).find()) continue;   // data-image-height="1500px//cdn..." 같은 깨진 값
                String v = at.getValue().trim();
                boolean anyUrl = key.contains("bg") || key.contains("background");
                if (v.startsWith("{") || v.startsWith("[")) {
                    c.addAll(urlsInJson(v, true));
                } else if (key.contains("srcset")) {
                    String best = bestFromSrcSet(v);
                    if (best != null) c.add(best);
                } else if (anyUrl ? looksLikeUrl(v) : looksLikeImageUrl(v)) {
                    c.add(v);
                }
            }
            out.addGroup(e, c);
        }

        // schema.org 마이크로데이터 이미지: <figure itemtype="http://schema.org/ImageObject" itemid="주소">
        for (Element e : doc.select("[itemtype~=(?i)schema\\.org/ImageObject]")) {
            List<String> c = new ArrayList<String>();
            if (looksLikeUrl(e.attr("itemid"))) c.add(e.attr("itemid"));
            for (Element u : e.select("[itemprop=url], [itemprop=contentUrl]")) {
                String v = u.hasAttr("content") ? u.attr("content") : u.attr("href");
                if (looksLikeUrl(v)) c.add(v);
            }
            out.addGroup(e, c);
        }

        // svg 안의 <image href>
        for (Element image : doc.select("svg image")) {
            String href = image.attr("href");
            out.add(image, href.isEmpty() ? image.attr("xlink:href") : href);
        }

        // 구조화 데이터(JSON-LD) 의 대표 이미지 (쇼핑몰/뉴스에서 원본 해상도인 경우가 많음)
        for (Element ld : doc.select("script[type=application/ld+json]")) {
            // 일부 CMS 는 JSON 안에도 &amp; / &#...; 를 그대로 넣는다
            for (String u : jsonLdImages(ld.data())) out.add(ld, u.contains("&") ? Parser.unescapeEntities(u, false) : u);
        }

        for (Element meta : doc.select("meta[property~=(?i)^og:image(:url|:secure_url)?$], meta[name~=(?i)^(og:image|twitter:image(:src)?)$], meta[itemprop=image]")) {
            out.add(meta, meta.attr("content"));
        }
        for (Element link : doc.select("link[rel~=(?i)^image_src$]")) {
            out.add(link, link.attr("href"));
        }

        for (Element a : doc.select("a[href]")) {
            if (!a.select("img, picture").isEmpty()) continue;   // img 를 감싼 링크는 위에서 처리
            String href = a.attr("href");
            if (isImageLink(href)) {
                out.add(a, href);
            }
        }

        // 인라인 style 및 <style> 안의 background-image
        for (Element e : doc.select("[style]")) {
            addCss(out, e, e.attr("style"));
        }
        for (Element style : doc.select("style")) {
            addCss(out, style, style.data());
        }
    }

    /** img 하나의 후보 주소들 (우선순위 순) */
    private static final Pattern PLACEHOLDER_SRC = Pattern.compile("(?i)(placeholder|blank|spacer|transparent|lazy|loading|empty|dummy|grey\\.|gray\\.|pixel)");

    /** @param sourcesGiven picture 의 &lt;source&gt; 에서 이미 원본 후보가 나왔는지 */
    private static List<String> imgCandidates(Element img, Element parent, Map<String, Integer> srcCount, boolean sourcesGiven) {
        List<String> c = new ArrayList<String>();
        // 1) 원본으로 연결된 링크: <a href="big.jpg"><img src="thumb.jpg"></a>
        if (parent != null && isWrappingImageLink(parent)) c.add(parent.attr("href"));
        // 2) srcset 계열 (srcset, data-srcset, data-lazy-srcset, data-lazy-retina ...) 의 가장 큰 후보
        c.addAll(srcsetCandidates(img));
        // 링크/srcset/알려진 지연 로딩·원본 속성에서 나온 후보는 "확실한 원본" → 이름이 다른 src(썸네일)는 버린다
        boolean strong = sourcesGiven || !c.isEmpty();
        // 3) 지연 로딩/원본 속성 (data-src, data-original, data-orig-file, data-zoom-image ...)
        for (Attribute at : img.attributes()) {
            String key = at.getKey().toLowerCase(Locale.ROOT);
            if (key.equals("src") || key.contains("srcset") || key.contains("retina")) continue;
            String v = at.getValue().trim();
            if (v.isEmpty() || v.contains("{{") || v.contains("{size}")) continue;
            if (v.startsWith("{") || v.startsWith("[")) {
                c.addAll(urlsInJson(v, false));          // data-src='{"default":{"src":"//...jpg"}}'
            } else if (key.startsWith("data-") || key.startsWith("lazy") || key.equals("original")) {
                if (NON_URL_ATTR.matcher(key).find() && !LAZY_SRC_ATTR.matcher(key).matches()) continue;
                boolean pathLike = v.contains("/") || LAZY_SRC_ATTR.matcher(key).matches();
                boolean nameSaysImage = IMG_URL_ATTR.matcher(key).matches() && IMAGE_NAME_ATTR.matcher(key).find();
                if (pathLike && (nameSaysImage ? looksLikeUrl(v) : looksLikeImageUrl(v))) {
                    c.add(v);
                    if (LAZY_SRC_ATTR.matcher(key).matches() || HIRES_ATTR.matcher(key).find()) strong = true;
                }
            }
        }
        // 4) src
        //    - 다른 후보가 없으면 src 사용
        //    - 같은 이미지의 작은 버전이면 함께 넣어 큰 쪽이 남게 한다
        //    - 확실한 원본 후보가 있으면 이름이 다른 src 는 썸네일/placeholder 이므로 버린다
        //    - 애매한 후보(이름 모를 data-* 속성)만 있으면 placeholder 가 아닌 src 는 함께 둔다
        String src = img.attr("src").trim();
        if (!src.isEmpty()) {
            if (c.isEmpty() && !sourcesGiven) {
                c.add(src);
            } else {
                String srcKey = ImageVariants.key(img.absUrl("src"));
                boolean sameImage = false;
                for (String x : c) {
                    if (ImageVariants.key(resolve(img, x)).equals(srcKey)) sameImage = true;
                }
                Integer repeat = srcCount.get(src);
                boolean placeholder = (repeat != null && repeat >= 3) || PLACEHOLDER_SRC.matcher(fileName(src)).find()
                        || (src.startsWith("data:") && src.length() < MIN_DATA_URI);
                if (sameImage || (!strong && !placeholder)) c.add(src);
            }
        }
        return c;
    }

    /** srcset 처럼 "주소 크기, 주소 크기" 형식인 모든 속성에서 각각 가장 큰 후보 */
    private static List<String> srcsetCandidates(Element e) {
        List<String> c = new ArrayList<String>();
        for (Attribute at : e.attributes()) {
            String key = at.getKey().toLowerCase(Locale.ROOT);
            if (key.contains("srcset") || key.contains("retina")) {
                String best = bestFromSrcSet(at.getValue());
                if (best != null) c.add(best);
            }
        }
        return c;
    }

    private static String fileName(String url) {
        String path = url.split("[?#]")[0];
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static boolean isWrappingImageLink(Element e) {
        return e.tagName().equals("a") && isImageLink(e.attr("href"));
    }

    private static String resolve(Element ctx, String value) {
        String abs = StringUtil.resolve(ctx.baseUri(), value.replace(" ", "%20"));
        return abs.isEmpty() ? value : abs;
    }

    /** JSON 문자열 안의 주소들 ('...' 와 "..." 모두, \/ 이스케이프 처리) */
    private static List<String> urlsInJson(String json, boolean requireImageExt) {
        List<String> c = new ArrayList<String>();
        Matcher m = JSON_URL.matcher(json.replace("\\/", "/").replace("\\u002F", "/").replace("\\u002f", "/"));
        while (m.find()) {
            String u = m.group(1);
            if (requireImageExt ? looksLikeImageUrl(u) : looksLikeUrl(u)) c.add(u);
        }
        return c;
    }

    /** JSON-LD 의 "image" / "thumbnailUrl" / "contentUrl" 값 (문자열, 배열, 중첩 객체 모두) */
    private static List<String> jsonLdImages(String json) {
        List<String> c = new ArrayList<String>();
        Matcher k = JSONLD_IMAGE_KEY.matcher(json);
        while (k.find()) {
            String value = balancedValue(json, k.end());
            if (value != null) c.addAll(urlsInJson(value, false));
        }
        return c;
    }

    /** start 위치의 JSON 값 하나 (문자열/배열/객체) 를 괄호 짝을 맞춰 잘라낸다 */
    private static String balancedValue(String s, int start) {
        if (start >= s.length()) return null;
        char first = s.charAt(start);
        if (first == '"') {
            int end = start + 1;
            while (end < s.length() && (s.charAt(end) != '"' || s.charAt(end - 1) == '\\')) end++;
            return s.substring(start, Math.min(end + 1, s.length()));
        }
        if (first != '[' && first != '{') return null;
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (inString) {
                if (ch == '"' && s.charAt(i - 1) != '\\') inString = false;
            } else if (ch == '"') {
                inString = true;
            } else if (ch == '[' || ch == '{') {
                depth++;
            } else if ((ch == ']' || ch == '}') && --depth == 0) {
                return s.substring(start, i + 1);
            }
        }
        return null;
    }

    private static void addCss(Collector out, Element ctx, String css) {
        if (css == null || css.isEmpty()) return;
        Matcher m = CSS_URL.matcher(css);
        while (m.find()) {
            String u = m.group(1);
            // 스타일시트 안에서는 이미지 확장자가 있는 것만 (폰트 등 제외)
            if (IMAGE_EXT.matcher(u).find() || u.startsWith("data:image")) {
                out.add(ctx, u);
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
            if (url.isEmpty() || url.startsWith("data:") && url.length() < MIN_DATA_URI) continue;   // placeholder
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

    /** 확장자와 상관없이 주소처럼 생겼는지 (지연 로딩 속성 값 판별용) */
    private static boolean looksLikeUrl(String v) {
        if (v == null) return false;
        v = v.trim();
        if (v.startsWith("data:image/")) return v.length() >= MIN_DATA_URI;
        if (v.isEmpty() || v.contains(" ") || v.startsWith("#") || v.startsWith("javascript:")) return false;
        return v.startsWith("http://") || v.startsWith("https://") || v.startsWith("//") || v.startsWith("/")
                || v.startsWith("./") || v.startsWith("../") || IMAGE_EXT.matcher(v).find();
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

    /** 로고/아이콘/svg 처럼 화면 장식일 가능성이 큰 이미지인지 (목록 뒤쪽에 배치) */
    public static boolean isMinor(String url) {
        return url != null && !url.startsWith("data:") && MINOR_URL.matcher(url).find();
    }

    /**
     * 찾은 주소를 모으는 곳.
     * - 확실한 잡음은 버리고, 로고/아이콘 같은 장식 이미지는 목록 뒤쪽에 둔다.
     * - 크기만 다른 같은 이미지는 하나로 합치고 가장 큰 주소를 남긴다 (위치는 처음 나온 곳).
     */
    static final class Collector {
        private final LinkedHashMap<String, String> main = new LinkedHashMap<String, String>();
        private final LinkedHashMap<String, String> minor = new LinkedHashMap<String, String>();

        /** 한 요소에서 나온 후보들: 같은 이미지는 가장 큰 것 하나만 */
        void addGroup(Element ctx, List<String> candidates) {
            for (String c : candidates) add(ctx, c);
        }

        void add(Element ctx, String raw) {
            String abs = absolute(ctx, raw);
            if (abs == null) return;
            String key = ImageVariants.key(abs);
            String old = main.containsKey(key) ? main.get(key) : minor.get(key);
            if (old != null) {
                if (ImageVariants.isBigger(abs, old)) {
                    if (main.containsKey(key)) main.put(key, abs);
                    else minor.put(key, abs);
                }
                return;
            }
            if (!abs.startsWith("data:") && MINOR_URL.matcher(abs).find()) minor.put(key, abs);
            else main.put(key, abs);
        }

        Set<String> result() {
            Set<String> r = new LinkedHashSet<String>(main.values());
            r.addAll(minor.values());
            return r;
        }
    }

    /** 절대 주소로 바꾸고 다운로드할 수 없는 것/잡음은 null */
    static String absolute(Element ctx, String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:image/")) {
            // 페이지에 직접 박힌 이미지. 작은 것은 placeholder 이므로 제외
            String compact = value.replaceAll("\\s", "");
            return compact.length() >= MIN_DATA_URI ? compact : null;
        }
        if (lower.startsWith("data:") || lower.startsWith("javascript:") || lower.startsWith("blob:") || lower.startsWith("about:")) {
            return null;
        }
        value = value.replace(" ", "%20").replace("&amp;", "&");
        // 브라우저처럼 너그럽게 해석 (java.net.URI 는 | ^ { } 가 든 주소를 통째로 거부한다)
        String base = ctx.baseUri();
        String abs = base == null || base.isEmpty() ? value : StringUtil.resolve(base, value);
        if (!(abs.startsWith("http://") || abs.startsWith("https://")) || JUNK_URL.matcher(abs).find()) return null;
        int hash = abs.indexOf('#');
        if (hash >= 0) abs = abs.substring(0, hash);
        // 루트 위로 올라가는 ../ 는 브라우저처럼 버린다 (http://a.com/../../x.jpg -> http://a.com/x.jpg)
        abs = abs.replaceFirst("^(https?://[^/]+)(/\\.\\.)+(?=/)", "$1");
        // 한글/움라우트 파일명은 브라우저처럼 %XX 로 (같은 그림이 두 표기로 나뉘지 않고, 어느 기기에서나 받아지게)
        return ImageVariants.encodeUrl(abs);
    }
}
