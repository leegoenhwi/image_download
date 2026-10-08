package com.personal_project.image_download.support;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 같은 이미지의 "크기만 다른 주소"(썸네일/리사이즈 버전)를 알아보고, 그중 가장 큰 것을 고른다.
 *
 * 예) 아래는 모두 같은 이미지로 본다.
 *   위키      .../thumb/a/ab/Cat.jpg/220px-Cat.jpg      ↔ .../a/ab/Cat.jpg
 *   워드프레스 photo-1024x683.jpg (genius: abc.300x300x1.jpg) ↔ photo.jpg
 *   블로거    .../s640/photo.png                          ↔ .../s1600/photo.png
 *   CDN 쿼리  photo.jpg?width=300&quality=85             ↔ photo.jpg?width=1300
 *   Cloudinary .../upload/w_400,c_fill/photo.jpg          ↔ .../upload/photo.jpg
 *   다음/티스토리 .../thumb/R750x0/?fname=X               ↔ .../thumb/R1280x0/?fname=X
 *   네이버    photo.jpg?type=w80_blur                     ↔ photo.jpg?type=w966
 */
public final class ImageVariants {

    /** 크기 표시가 없는 주소는 원본으로 보고 가장 크다고 취급 */
    static final int ORIGINAL = 100000;

    private static final Pattern WIKI_THUMB = Pattern.compile("/thumb(/[0-9a-f]/[0-9a-f]{2}/[^/]+)/(\\d+)px-[^/]+$");
    /** 파일명 끝의 -1024x683 (워드프레스 등) */
    private static final Pattern FILE_WxH = Pattern.compile("[-_.](\\d{2,5})x(\\d{2,5})(x\\d{1,2})?(?=\\.[A-Za-z0-9]{2,5}$)");
    /** 파일명 끝의 크기 이름 (뉴욕타임스 등) */
    private static final Pattern FILE_NAMED = Pattern.compile(
            "-(superJumbo|jumbo|articleLarge|articleInline|facebookJumbo|thumbLarge|thumbStandard|thumbWide|popup|filmstrip"
                    + "|mobileMasterAt3x|sfSpan|hp[A-Z][A-Za-z]*|blog\\d+|master\\d+|square\\d+|watch\\d+"
                    + "|[a-z]+(?:ThreeByTwo|SixteenByNine|TwoByThree|Horizontal|Vertical)[A-Za-z0-9]*)(-v\\d+)?(?=\\.[A-Za-z0-9]{2,5}$)");
    private static final Pattern SEG_WxH = Pattern.compile("(\\d{2,5})x(\\d{0,5})[!^>]?");   // 1200x630! (brightspot)
    private static final Pattern SEG_XH = Pattern.compile("x(\\d{2,5})");
    private static final Pattern SEG_BLOGGER = Pattern.compile("([swh])(0|\\d{2,5})(-[a-z0-9-]+)?");
    private static final Pattern SEG_BLOGGER_WH = Pattern.compile("w(\\d{2,5})-h(\\d{2,5})(-[a-z0-9-]+)?");
    private static final Pattern SEG_PX = Pattern.compile("(\\d{2,5})(px|w)");
    private static final Pattern SEG_DAUM = Pattern.compile("[RCT](\\d{2,5})x(\\d{1,5})(\\.q\\d+)?");
    private static final Pattern SEG_COLON = Pattern.compile("(?:resize|fit|format|quality)[:=][^/]*");
    private static final Pattern TRANSFORM_PART = Pattern.compile("[a-z]{1,4}_[^,]+");
    private static final Pattern DIGITS = Pattern.compile("(\\d{2,5})");

    private static final Set<String> SIZE_WORDS = new HashSet<String>();
    private static final Map<String, Integer> WORD_SIZE = new HashMap<String, Integer>();
    private static final Set<String> SIZE_PARAMS = new HashSet<String>();

    static {
        String[][] words = {
                {"thumb", "150"}, {"thumbs", "150"}, {"thumbnail", "150"}, {"thumbnails", "150"}, {"xs", "100"},
                {"small", "320"}, {"sm", "320"}, {"preview", "400"}, {"medium", "640"}, {"md", "640"}, {"sd", "640"},
                {"large", "1024"}, {"lg", "1024"}, {"big", "1024"}, {"hd", "1280"}, {"xlarge", "2048"}, {"xl", "2048"},
                {"orig", "" + ORIGINAL}, {"original", "" + ORIGINAL}, {"originals", "" + ORIGINAL},
                {"full", "" + ORIGINAL}, {"fullsize", "" + ORIGINAL},
                {"resize", "0"}, {"resized", "0"}, {"crop", "0"}, {"cropped", "0"}, {"square", "0"},
                {"max", "0"}, {"fit", "0"}, {"c", "0"}};
        for (String[] w : words) {
            SIZE_WORDS.add(w[0]);
            WORD_SIZE.put(w[0], Integer.parseInt(w[1]));
        }
        String[][] named = {{"superJumbo", "2048"}, {"jumbo", "1024"}, {"facebookJumbo", "1050"}, {"articleLarge", "600"},
                {"articleInline", "190"}, {"popup", "650"}, {"thumbLarge", "150"}, {"thumbStandard", "75"},
                {"thumbWide", "190"}, {"filmstrip", "210"}, {"mobileMasterAt3x", "1800"}, {"sfSpan", "400"}};
        for (String[] w : named) WORD_SIZE.put(w[0], Integer.parseInt(w[1]));
        String[] params = {"w", "h", "width", "height", "imwidth", "imheight", "wid", "hei", "mw", "mh", "sw", "sh",
                "maxwidth", "maxheight", "resize", "fit", "crop", "rect", "quality", "q", "qlt", "auto", "format", "fm",
                "dpr", "s", "sig", "itok", "ixlib", "ixid", "type", "size", "strip", "ssl", "zoom", "mode", "scale",
                "upscale", "disable", "compress", "cs", "op_sharpen", "im", "impolicy", "output-quality", "output-format",
                "cb", "v", "ver", "version", "ts", "t", "x", "y", "l", "o", "m",
                // 추적/캐시/오버레이용 (이미지 내용과 무관)
                "mbid", "is-pending-load", "k", "tw", "year", "ar", "enable", "f", "signature", "cropupalias", "template",
                "fbclid", "gclid", "ref", "modified_at", "picto", "ratio_x", "ratio_y", "op", "client", "overlay-align",
                "overlay-width", "overlay-base64", "lossy", "ssl", "_"};
        for (String p : params) SIZE_PARAMS.add(p);
    }

    private ImageVariants() {
    }

    /** 주소 분석 결과: 같은 이미지끼리 같은 key, size 는 클수록 큰 이미지 */
    static final class Info {
        final String key;
        final int size;

        Info(String key, int size) {
            this.key = key;
            this.size = size;
        }
    }

    public static String key(String url) {
        return analyze(url).key;
    }

    public static int size(String url) {
        return analyze(url).size;
    }

    /** candidate 가 current 보다 큰 버전이면 true (같은 이미지라는 전제) */
    public static boolean isBigger(String candidate, String current) {
        return analyze(candidate).size > analyze(current).size;
    }

    /** 크기만 다른 주소를 하나로 합친다. 순서는 처음 나온 위치를 유지하고 주소는 가장 큰 것으로. */
    public static Set<String> dedupe(Collection<String> urls) {
        Map<String, String> best = new LinkedHashMap<String, String>();
        Map<String, Integer> sizes = new HashMap<String, Integer>();
        for (String u : urls) {
            Info info = analyze(u);
            Integer old = sizes.get(info.key);
            if (old == null || info.size > old) {
                best.put(info.key, u);
                sizes.put(info.key, info.size);
            }
        }
        return new LinkedHashSet<String>(best.values());
    }

    static Info analyze(String url) {
        return analyze(url, 0);
    }

    private static Info analyze(String url, int depth) {
        if (url == null || url.startsWith("data:")) return new Info(String.valueOf(url), ORIGINAL);
        // 이미지 프록시/리사이저: 안에 품은 원본 주소 기준으로 묶는다
        //   t2.genius.com/unsafe/128x128/https%3A%2F%2Fimages.genius.com%2Fabc.jpg
        //   images.site.com/resize?width=370&url=https://cdn.site.com/abc.jpg
        if (depth < 3) {
            String[] split = splitEmbedded(url);
            if (split != null) {
                String inner = split[1];
                Info outer = analyzePlain(split[0]);   // 프록시 자체의 크기 표시만 (안쪽 주소의 크기 표시는 빼고)
                Info in = analyze(inner, depth + 1);
                return new Info(in.key, outer.size >= ORIGINAL - 1 ? ORIGINAL - 2 : outer.size);
            }
        }
        return analyzePlain(url);
    }

    private static final Pattern EMBEDDED = Pattern.compile("(?i)(https?:/{1,2}|https?%3A%2F%2F)");

    /** 주소 안에 다른 이미지 주소(쿼리 값 또는 경로 뒷부분)가 있으면 {그 부분을 뺀 프록시 주소, 안쪽 주소}, 없으면 null */
    private static String[] splitEmbedded(String url) {
        int q = url.indexOf('?');
        if (q > 0) {
            String[] pairs = url.substring(q + 1).split("&");
            for (int i = 0; i < pairs.length; i++) {
                String pair = pairs[i];
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                String v = decode(pair.substring(eq + 1));
                if ((v.startsWith("http://") || v.startsWith("https://") || v.startsWith("//")) && hasImageExt(v)) {
                    StringBuilder outer = new StringBuilder(url.substring(0, q + 1));
                    for (int j = 0; j < pairs.length; j++) {
                        if (j == i) continue;
                        if (outer.charAt(outer.length() - 1) != '?') outer.append('&');
                        outer.append(pairs[j]);
                    }
                    return new String[]{outer.toString(), v.startsWith("//") ? "https:" + v : v};
                }
            }
        }
        String path = q > 0 ? url.substring(0, q) : url;
        Matcher m = EMBEDDED.matcher(path);
        int start = -1;
        while (m.find()) {
            if (m.start() > 8) {
                start = m.start();
                break;
            }
        }
        if (start < 0) return null;
        String inner = decode(url.substring(start)).replaceFirst("^(https?):/(?!/)", "$1://");
        return hasImageExt(inner) ? new String[]{url.substring(0, start), inner} : null;
    }

    private static boolean hasImageExt(String u) {
        String p = u.split("[?#]")[0].toLowerCase(Locale.ROOT);
        return p.matches(".*\\.(jpe?g|png|gif|webp|avif|bmp|heic)$");
    }

    private static String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static Info analyzePlain(String url) {
        URI uri;
        try {
            uri = new URI(url.replace(" ", "%20").replace("|", "%7C").replace("^", "%5E"));
        } catch (Exception e) {
            return new Info(url, ORIGINAL);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        int size = -1;

        // 워드프레스 Jetpack CDN: i0.wp.com/site.com/... == site.com/...
        if (host.matches("i\\d\\.wp\\.com") && path.length() > 1) {
            int slash = path.indexOf('/', 1);
            if (slash > 0) {
                host = path.substring(1, slash).toLowerCase(Locale.ROOT);
                path = path.substring(slash);
            }
        }
        if (host.startsWith("www.")) host = host.substring(4);

        Matcher wiki = WIKI_THUMB.matcher(path);
        if (wiki.find()) {
            size = Math.max(size, Integer.parseInt(wiki.group(2)));
            path = path.substring(0, wiki.start()) + wiki.group(1);
        }

        // 경로 조각 중 크기 표시 제거
        String[] segs = path.split("/", -1);
        StringBuilder sb = new StringBuilder();
        boolean prevRemoved = false;
        for (int i = 0; i < segs.length; i++) {
            String seg = segs[i];
            boolean last = i == segs.length - 1;
            int s = last ? -2 : segmentSize(seg, prevRemoved);
            if (s != -2) {
                size = Math.max(size, s);
                prevRemoved = true;
                continue;
            }
            prevRemoved = false;
            if (i > 0) sb.append('/');
            sb.append(seg);
        }
        path = sb.toString();

        // 파일명 끝 크기 표시
        Matcher m = FILE_WxH.matcher(path);
        boolean outer = true;
        while (m.find()) {
            if (outer) size = Math.max(size, Integer.parseInt(m.group(1)));
            outer = false;
            path = path.substring(0, m.start()) + path.substring(m.end());
            m = FILE_WxH.matcher(path);
        }
        m = FILE_NAMED.matcher(path);
        if (m.find()) {
            Integer named = WORD_SIZE.get(m.group(1));
            Matcher d = DIGITS.matcher(m.group(1));
            size = Math.max(size, named != null ? named : d.find() ? Integer.parseInt(d.group(1)) : 500);
            path = path.substring(0, m.start()) + path.substring(m.end());
        }

        // 쿼리: 크기/품질/서명 파라미터는 빼고 나머지만 key 에 포함
        TreeMap<String, String> keep = new TreeMap<String, String>();
        String query = uri.getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                String k = (eq < 0 ? pair : pair.substring(0, eq)).toLowerCase(Locale.ROOT);
                String v = eq < 0 ? "" : pair.substring(eq + 1);
                if (k.equals("name") && v.matches("(?i)small|medium|large|orig|thumb|\\d+x\\d+")) {
                    size = Math.max(size, v.equalsIgnoreCase("orig") ? ORIGINAL : twitterSize(v));
                    continue;
                }
                if (eq < 0 || k.startsWith("utm_")) continue;   // ?1550179390, ?ver-20200423 같은 캐시 무효화 값
                try {
                    v = java.net.URLDecoder.decode(v, "UTF-8");     // url=https%3A// 와 url=https%3A%2F%2F 를 같게
                } catch (Exception ignored) {
                }
                if (SIZE_PARAMS.contains(k)) {
                    if (k.equals("w") || k.equals("width") || k.equals("imwidth") || k.equals("wid") || k.equals("mw")
                            || k.equals("sw") || k.equals("maxwidth") || k.equals("resize") || k.equals("fit")
                            || k.equals("size") || k.equals("type")) {
                        Matcher d = DIGITS.matcher(v);
                        if (d.find()) size = Math.max(size, Integer.parseInt(d.group(1)));
                    }
                    continue;
                }
                keep.put(k, v);
            }
        }
        StringBuilder key = new StringBuilder(host).append(path);
        if (!keep.isEmpty()) {
            key.append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : keep.entrySet()) {
                if (!first) key.append('&');
                key.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
        }
        return new Info(key.toString(), size < 0 ? ORIGINAL : size == 0 ? ORIGINAL - 1 : size);
    }

    /** 경로 조각이 크기 표시면 그 크기(모르면 0), 아니면 -2 */
    private static int segmentSize(String seg, boolean prevRemoved) {
        if (seg.isEmpty()) return -2;
        String lower = seg.toLowerCase(Locale.ROOT);
        Matcher m;
        if ((m = SEG_WxH.matcher(seg)).matches()) return Math.max(num(m.group(1)), num(m.group(2)));
        if ((m = SEG_XH.matcher(seg)).matches()) return num(m.group(1));
        if ((m = SEG_BLOGGER_WH.matcher(seg)).matches()) return num(m.group(1));
        if ((m = SEG_BLOGGER.matcher(seg)).matches()) return m.group(2).equals("0") ? ORIGINAL : num(m.group(2));
        if ((m = SEG_PX.matcher(seg)).matches()) return num(m.group(1));
        if ((m = SEG_DAUM.matcher(seg)).matches()) return num(m.group(1));
        if (SEG_COLON.matcher(lower).matches()) {
            Matcher d = DIGITS.matcher(seg);
            return d.find() ? num(d.group(1)) : 0;
        }
        if (SIZE_WORDS.contains(lower)) return WORD_SIZE.get(lower);
        // medium: /fit/c/96/96/, /max/1024/ 처럼 크기 단어 뒤의 숫자
        if (prevRemoved && seg.matches("\\d{1,4}")) return num(seg);
        // Cloudinary/imgix 계열 변환 조각: w_400,h_300,c_fill  /  t_fit-760w,f_auto
        String decoded = seg.replace("%2C", ",").replace("%2c", ",");
        boolean transform = true;
        int width = 0;
        for (String part : decoded.split(",")) {
            if (!TRANSFORM_PART.matcher(part).matches()) {
                transform = false;
                break;
            }
            Matcher d = DIGITS.matcher(part);
            if ((part.startsWith("w_") || part.startsWith("t_")) && d.find()) width = Math.max(width, num(d.group(1)));
        }
        return transform && decoded.contains("_") ? width : -2;
    }

    private static int twitterSize(String v) {
        if (v.equalsIgnoreCase("large")) return 2048;
        if (v.equalsIgnoreCase("medium")) return 1200;
        if (v.equalsIgnoreCase("small")) return 680;
        if (v.equalsIgnoreCase("thumb")) return 150;
        Matcher d = DIGITS.matcher(v);
        return d.find() ? num(d.group(1)) : 0;
    }

    private static int num(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 테스트/디버깅용: 같은 key 로 묶인 그룹 */
    static List<List<String>> groups(Collection<String> urls) {
        Map<String, List<String>> g = new LinkedHashMap<String, List<String>>();
        for (String u : urls) {
            String k = key(u);
            List<String> l = g.get(k);
            if (l == null) {
                l = new ArrayList<String>();
                g.put(k, l);
            }
            l.add(u);
        }
        return new ArrayList<List<String>>(g.values());
    }
}
