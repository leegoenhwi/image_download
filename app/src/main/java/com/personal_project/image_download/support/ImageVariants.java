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
    //   타이머(Timber) photo-280x0-c-default.jpg, 크롭 표시 3195813-345x230c-name.jpg 포함
    private static final Pattern FILE_WxH = Pattern.compile("[-_.](\\d{2,5})x(\\d{2,5}|0)([a-z]|x\\d{1,2})?(-c-[a-z]+)?(?=[-_.@][^/]*$)");
    /** 파일명 전체가 크기: .../v0/1200x-1.jpg (블룸버그) */
    private static final Pattern FILE_ONLY_WxH = Pattern.compile("/(\\d{1,5})x(-?\\d{1,5})(?=(\\.[A-Za-z0-9]{2,5})?$)");
    /** futurecdn 등: <긴 id>-1280-80.jpg (너비-품질) */
    private static final Pattern FILE_ID_WIDTH_Q = Pattern.compile("([-/][0-9A-Za-z]{16,})-(\\d{3,4})-\\d{2,3}(?=\\.[A-Za-z0-9]{2,5}$)");
    /** 크기 단어 꼬리: gallery_large.jpg, photo__medium.jpg, logo-small.png */
    private static final Pattern FILE_SIZE_WORD = Pattern.compile(
            "[-_]+(xx?small|small|medium|large|xx?large|big|tiny|thumb|original|orig|full|retina)(?=\\.[A-Za-z0-9]{2,5}$)", Pattern.CASE_INSENSITIVE);
    /** Shopify: photo_2000x.jpg, photo_400x400_crop_center.jpg, photo_grande.jpg */
    private static final Pattern SHOPIFY_SIZE = Pattern.compile(
            "_(\\d{2,5}x\\d{0,5}|\\d{0,5}x\\d{2,5}|pico|icon|thumb|small|compact|medium|large|grande|original|master)(_crop_[a-z]+)?(@\\dx)?(?=\\.[A-Za-z0-9]{2,5}$)");
    /** 워드프레스 큰 이미지 축소본: photo-scaled.jpg (최대 2560), 회전본 photo-rotated.jpg */
    private static final Pattern FILE_WP_SCALED = Pattern.compile("-(scaled|rotated)(?=\\.[A-Za-z0-9]{2,5}$)");
    /** 이름 속 너비: 58215_default-2280w_1uF.jpg */
    private static final Pattern FILE_DASH_W = Pattern.compile("-(\\d{3,4})w(?=[_.][^/]*$)");
    /** OpenCms: name.jpg_396674193.jpg */
    private static final Pattern FILE_OPENCMS = Pattern.compile("(\\.(?:jpe?g|png|gif|webp))_\\d{6,}(?=\\.[A-Za-z0-9]{2,5}$)", Pattern.CASE_INSENSITIVE);
    /** imgproxy: /<서명>/rt:fill/w:640/h:300/g:ce/plain/<원본 경로> */
    private static final Pattern IMGPROXY = Pattern.compile("^/[A-Za-z0-9_-]{16,}((?:/[a-z_]{1,20}:[^/]*)+)/plain/");
    private static final Pattern IMGPROXY_WIDTH = Pattern.compile("/(?:w|width|s|size):(\\d{2,5})");
    /** Jimdo: /transf/dimension=331x10000:format=png/ , /transf/none/ */
    private static final Pattern JIMDO = Pattern.compile("/transf/([^/]+)/");
    /** webp-express 플러그인 사본 경로 */
    private static final Pattern WEBP_EXPRESS = Pattern.compile("/wp-content/webp-express/webp-images/(doc-root/|uploads/)");
    /** Pimcore: name~-~768w.png, Webflow: name-p-500.jpeg, 해시 뒤 너비: abc-<32자 해시>-768.webp */
    private static final Pattern FILE_PIMCORE = Pattern.compile("~-~(\\d{2,5})w(?=[@.][^/]*$)");
    private static final Pattern FILE_WEBFLOW = Pattern.compile("-p-(\\d{3,4})(?=\\.[A-Za-z0-9]{2,5}$)");
    private static final Pattern FILE_HASH_WIDTH = Pattern.compile("([-/][0-9a-f]{16,})-(\\d{3,4})(-\\d{2,3})?(?=\\.[A-Za-z0-9]{2,5}$)");
    /** CNN: name-exlarge-169.jpg / name-small-169.jpg */
    private static final Pattern FILE_CNN = Pattern.compile(
            "-(xsmall|small|medium|medium-plus|large|exlarge|super|full|story-body|tease|hp-video|live-video|overlay|horizontal-large|horizontal-gallery)-(\\d{2,3})(?=\\.[A-Za-z0-9]{2,5}$)");
    /** ARD 계열(WDR 등): name-100~_v-ARDFotogalerie.jpg */
    private static final Pattern FILE_ARD = Pattern.compile("~_v-([A-Za-z0-9]+)(?=\\.[A-Za-z0-9]{2,5}$)");
    /** 반응형 파일명: name_1875_rdax_1080x608_70.jpg / name_1875_rdax_60.jpg */
    private static final Pattern FILE_RDAX = Pattern.compile("_rdax(_(\\d{2,5})x\\d{2,5})?_\\d{2,3}(?=\\.[A-Za-z0-9]{2,5}$)");
    /** TYPO3 처리본: /_processed_/b/e/csm_name_f3cc1d28fb.jpg */
    private static final Pattern TYPO3_PROCESSED = Pattern.compile("/_processed_/[0-9a-f]/[0-9a-f]/csm_([^/]+)_[0-9a-f]{10}(?=\\.[A-Za-z0-9]{2,5}$)");
    /** Pimcore 썸네일 폴더: /image-thumb__18095__step-by-step-item-md/ */
    private static final Pattern PIMCORE_DIR = Pattern.compile("/image-thumb__(\\d+)__[^/]+/");
    /** 크롭/크기 묶음 조각: 104081_11990_1_C_1920_1080_0_4838821 */
    private static final Pattern SEG_CROP_WH = Pattern.compile("(_[A-Z])_(\\d{2,5})_(\\d{2,5})(?=_\\d+_\\d+/)");
    /** 르몽드: /2022/07/18/3/0/6016/4010/360/0/95/0/file.jpg */
    private static final Pattern LEMONDE = Pattern.compile("^(/\\d{4}/\\d{2}/\\d{2})/\\d+/\\d+/\\d+/\\d+/(\\d+)/\\d+/\\d+/\\d+/");
    /** Motorsport Network CDN: /images/mgl/rxPGx/s1/... (s1 이 가장 큼) */
    private static final Pattern MOTOR1_SIZE = Pattern.compile("/s([1-9])/(?=[^/]+$)");
    /** Vox/뉴욕매거진: name.2x.rsquare.w536.jpg */
    private static final Pattern FILE_PYXIS = Pattern.compile("(\\.(\\d)x)?(\\.r[a-z]+)?(\\.h\\d{2,5})?\\.w(\\d{2,5})(\\.h\\d{2,5})?(?=\\.[A-Za-z0-9]{2,5}$)");
    /** 기기별 버전: logo_tablet.png, hero-mobile.jpg */
    private static final Pattern FILE_DEVICE = Pattern.compile("[-_](mobile|tablet|desktop|phone)(?=\\.[A-Za-z0-9]{2,5}$)", Pattern.CASE_INSENSITIVE);
    /** 워드프레스 이미지 편집본 표시: photo-e1542577367541-700x467.jpg */
    private static final Pattern FILE_WP_EDIT = Pattern.compile("-e\\d{13}(?=[-.][^/]*$)");
    /** 같은 그림의 다른 형식: photo.jpg / photo.webp / photo.jpg.webp (gif 는 움짤일 수 있어 제외) */
    private static final Pattern FILE_FORMAT = Pattern.compile("(\\.(jpe?g|png|webp|avif|jxl|img))+$", Pattern.CASE_INSENSITIVE);
    /** Drupal 이미지 스타일: /styles/article_main_small/public/... */
    private static final Pattern DRUPAL_STYLE = Pattern.compile("/styles/[^/]+/(public|private)/");
    /** Magnolia CMS: /.imaging/mte/<테마>/<변형>/dam/... */
    private static final Pattern MAGNOLIA = Pattern.compile("/\\.imaging/(mte|stk)/[^/]+/([^/]+)/");
    /** 크기 조각: 2280_auto_1_1_0 (mtb-news 등) */
    private static final Pattern SEG_UNDERSCORE_WxH = Pattern.compile("(\\d{2,5})_(auto|\\d{2,5})(_\\d+){2,}");
    /** django easy-thumbnails: photo.png__1140x0_q85_crop.png → photo.png */
    private static final Pattern FILE_EASY_THUMB = Pattern.compile("(\\.[A-Za-z0-9]{2,5})__(\\d{2,5})x(\\d{1,5})[^/]*$");
    /** 파일명 속 리사이즈 파라미터: abc_extract=0,0,1867,1050_resize=1360,765_.jpg */
    private static final Pattern FILE_PARAMS = Pattern.compile("_(extract|resize|crop|fit|quality|q|w|h|width|height)=[^_/]+(?=[^/]*$)");
    private static final Pattern FILE_TRAILING_UNDERSCORE = Pattern.compile("_+(?=\\.[A-Za-z0-9]{2,5}$)");
    /** 레티나: photo@2x.png */
    private static final Pattern FILE_RETINA = Pattern.compile("@(\\d(?:\\.\\d)?)x(?=\\.[A-Za-z0-9]{2,5}$)");
    /** 썸네일 접미사: photo_thumb.png, photo-thumbnail.jpg, imga131_thumb1.png */
    private static final Pattern FILE_THUMB_SUFFIX = Pattern.compile("[-_]thumb(?:nail)?s?\\d*(?=\\.[A-Za-z0-9]{2,5}$)", Pattern.CASE_INSENSITIVE);
    /** 뉴욕타임스 계열 크기 이름(카멜표기): -videoLarge.jpg, -threeByTwoSmallAt2X.jpg */
    private static final Pattern NYT_NAMED = Pattern.compile("-([a-z]+[A-Z][A-Za-z]*\\d*)(-v\\d+)?(?=\\.[A-Za-z0-9]{2,5}$)");
    /** 파일명 끝의 크기 이름 (뉴욕타임스 등) */
    private static final Pattern FILE_NAMED = Pattern.compile(
            "-(superJumbo|jumbo|articleLarge|articleInline|facebookJumbo|thumbLarge|thumbStandard|thumbWide|popup|filmstrip"
                    + "|mobileMasterAt3x|sfSpan|hp[A-Z][A-Za-z]*|blog\\d+|master\\d+|square\\d+|watch\\d+"
                    + "|[a-z]+(?:ThreeByTwo|SixteenByNine|TwoByThree|Horizontal|Vertical)[A-Za-z0-9]*)(-v\\d+)?(?=\\.[A-Za-z0-9]{2,5}$)");
    private static final Pattern SEG_WxH = Pattern.compile("(\\d{2,5})x(\\d{0,5})[!^>]?");   // 1200x630! (brightspot)
    private static final Pattern SEG_XH = Pattern.compile("x(\\d{2,5})");
    private static final Pattern SEG_BLOGGER = Pattern.compile("([swh])(0|\\d{2,5})[a-z]?(-[a-z0-9-]+)?");   // s615, s270b (Reach)
    private static final Pattern SEG_ZERO_H = Pattern.compile("0x(\\d{2,5})");
    private static final Pattern SEG_ASPECT = Pattern.compile("\\d{1,2}x\\d{1,2}");
    private static final Pattern SEG_BLOGGER_WH = Pattern.compile("w(\\d{2,5})-h(\\d{2,5})(-[a-z0-9-]+)?");
    private static final Pattern SEG_PX = Pattern.compile("(\\d{2,5})(px|w)");
    private static final Pattern SEG_DAUM = Pattern.compile("[RCT](\\d{2,5})x(\\d{1,5})(\\.q\\d+)?");
    private static final Pattern SEG_COLON = Pattern.compile("(?:resize|fit|format|quality|filters|dimension)[:=][^/]*");
    /** Thumbor 계열 리사이저의 서명 조각: /resizer/Ys2lyOgzyBq70esAgpOnDTO7gU4=/233x159/... */
    private static final Pattern SEG_SIGNATURE = Pattern.compile("[A-Za-z0-9_-]{20,}={1,2}");
    /** 파일명 앞의 썸네일 표시: thumbs_photo.jpg, tn_photo.jpg (NextGEN 갤러리 등) */
    private static final Pattern FILE_THUMB_PREFIX = Pattern.compile("/(thumbs?|tn)[_-](?=[^/]+$)");
    private static final Pattern TRANSFORM_PART = Pattern.compile("[a-z]{1,4}_[^,]+");
    private static final Pattern DIGITS = Pattern.compile("(\\d{2,5})");

    private static final Set<String> SIZE_WORDS = new HashSet<String>();
    /** image_full_tab, image_thumb_desk_narrow 같은 조각에 함께 쓰이는 단어 (크기 단어가 하나는 있어야 함) */
    private static final Set<String> SEG_FILLER = new HashSet<String>(java.util.Arrays.asList(
            "image", "images", "img", "photo", "pic", "picture", "tab", "tablet", "palm", "desk", "desktop", "mobile",
            "phone", "narrow", "wide", "retina", "size"));
    private static final Set<String> SEG_DEVICE = new HashSet<String>(java.util.Arrays.asList(
            "tab", "tablet", "palm", "desk", "desktop", "mobile", "phone", "narrow", "wide", "retina"));
    private static final Map<String, Integer> WORD_SIZE = new HashMap<String, Integer>();
    private static final Set<String> SIZE_PARAMS = new HashSet<String>();
    private static final Map<String, Integer> CNN_SIZE = new HashMap<String, Integer>();

    static {
        String[][] words = {
                {"thumb", "150"}, {"thumbs", "150"}, {"thumbnail", "150"}, {"thumbnails", "150"}, {"xs", "100"},
                {"small", "320"}, {"sm", "320"}, {"preview", "400"}, {"medium", "640"}, {"md", "640"}, {"sd", "640"},
                {"large", "1024"}, {"lg", "1024"}, {"big", "1024"}, {"hd", "1280"}, {"xlarge", "2048"}, {"xl", "2048"},
                {"orig", "" + ORIGINAL}, {"original", "" + ORIGINAL}, {"originals", "" + ORIGINAL},
                {"full", "" + ORIGINAL}, {"fullsize", "" + ORIGINAL},
                {"resize", "0"}, {"resized", "0"}, {"crop", "0"}, {"cropped", "0"}, {"square", "0"},
                {"max", "0"}, {"fit", "0"}, {"c", "0"},
                // Thumbor 계열 리사이저 옵션
                {"unsafe", "0"}, {"smart", "0"}, {"fit-in", "0"}, {"adaptive-fit-in", "0"}, {"full-fit-in", "0"}, {"trim", "0"}};
        for (String[] w : words) {
            SIZE_WORDS.add(w[0]);
            WORD_SIZE.put(w[0], Integer.parseInt(w[1]));
        }
        String[][] named = {{"superJumbo", "2048"}, {"jumbo", "1024"}, {"facebookJumbo", "1050"}, {"articleLarge", "600"},
                {"articleInline", "190"}, {"popup", "650"}, {"thumbLarge", "150"}, {"thumbStandard", "75"},
                {"thumbWide", "190"}, {"filmstrip", "210"}, {"mobileMasterAt3x", "1800"}, {"sfSpan", "400"},
                {"videoLarge", "768"}, {"videoSmall", "300"}, {"hpSmall", "250"}, {"hpMedium", "400"}, {"hpLarge", "600"}};
        for (String[] w : named) WORD_SIZE.put(w[0], Integer.parseInt(w[1]));
        String[][] cnn = {{"xsmall", "200"}, {"small", "460"}, {"tease", "300"}, {"medium", "640"}, {"medium-plus", "780"},
                {"large", "1100"}, {"exlarge", "1600"}, {"super", "2000"}, {"full", "" + ORIGINAL}};
        for (String[] w : cnn) CNN_SIZE.put(w[0], Integer.parseInt(w[1]));
        String[] params = {"w", "h", "width", "height", "imwidth", "imheight", "wid", "hei", "mw", "mh", "sw", "sh",
                "maxwidth", "maxheight", "resize", "fit", "crop", "rect", "quality", "q", "qlt", "auto", "format", "fm",
                "dpr", "s", "sig", "itok", "ixlib", "ixid", "type", "size", "strip", "ssl", "zoom", "mode", "scale",
                "upscale", "disable", "compress", "cs", "op_sharpen", "im", "impolicy", "output-quality", "output-format",
                "cb", "v", "ver", "version", "ts", "t", "x", "y", "l", "o", "m",
                // 추적/캐시/오버레이용 (이미지 내용과 무관)
                "mbid", "is-pending-load", "k", "tw", "year", "ar", "enable", "f", "signature", "cropupalias", "template",
                "fbclid", "gclid", "ref", "modified_at", "picto", "ratio_x", "ratio_y", "op", "client", "overlay-align",
                "overlay-width", "overlay-base64", "lossy", "ssl", "_", "hash"};
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

    /**
     * 브라우저처럼 경로/쿼리 속 ASCII 밖 문자(한글, 움라우트 …)와 공백 등을 UTF-8 %XX 로 바꾼다. 호스트와 이미 있는 %XX 는 그대로.
     * 예) http://a.de/Größe.jpg → http://a.de/Gr%C3%B6%C3%9Fe.jpg
     */
    public static String encodeUrl(String url) {
        if (url == null || url.startsWith("data:")) return url;
        int scheme = url.indexOf("://");
        int start = scheme < 0 ? 0 : scheme + 3;
        while (start < url.length() && "/?#".indexOf(url.charAt(start)) < 0) start++;   // 호스트 건너뛰기
        StringBuilder sb = null;
        boolean inPath = true;
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '?' || c == '#') inPath = false;
            boolean encode = c <= 0x20 || c >= 0x7F || c == '"' || c == '<' || c == '>'
                    || (inPath && (c == '`' || c == '{' || c == '}'));
            if (!encode) {
                if (sb != null) sb.append(c);
                continue;
            }
            if (sb == null) sb = new StringBuilder(url.length() + 16).append(url, 0, i);
            int end = i + (Character.isHighSurrogate(c) && i + 1 < url.length() ? 2 : 1);
            byte[] bytes;
            try {
                bytes = url.substring(i, end).getBytes("UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                bytes = new byte[]{'?'};
            }
            for (byte b : bytes) sb.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
            i = end - 1;
        }
        return sb == null ? url : sb.toString();
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

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
        url = encodeUrl(url.replace("&amp;", "&"));   // HTML 이 두 번 escape 된 주소, 한글 파일명은 %XX 로 통일
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

        Matcher wx = WEBP_EXPRESS.matcher(path);
        if (wx.find()) path = path.substring(0, wx.start()) + (wx.group(1).equals("uploads/") ? "/wp-content/uploads/" : "/") + path.substring(wx.end());
        Matcher proxy = IMGPROXY.matcher(path);
        if (proxy.find()) {
            Matcher w = IMGPROXY_WIDTH.matcher(proxy.group(1));
            size = Math.max(size, w.find() ? num(w.group(1)) : 0);
            path = "/plain/" + path.substring(proxy.end());
        }
        Matcher jimdo = JIMDO.matcher(path);
        if (jimdo.find()) {
            Matcher d = DIGITS.matcher(jimdo.group(1));
            size = Math.max(size, jimdo.group(1).equals("none") ? ORIGINAL : d.find() ? num(d.group(1)) : 0);
            path = path.substring(0, jimdo.start()) + "/transf/" + path.substring(jimdo.end());
        }
        // 숫자만 다른 분산 호스트(img1/img2, cdn2/cdn3 …)는 같은 서버로
        int dot = host.indexOf('.');
        if (dot > 0 && host.indexOf('.', dot + 1) > 0) {
            String label = host.substring(0, dot);
            if (label.matches("[a-z-]*[a-z]\\d{1,2}(-[a-z]+)?")) host = label.replaceAll("\\d+", "") + host.substring(dot);
            if (host.startsWith("www.")) host = host.substring(4);
        }
        Matcher style = DRUPAL_STYLE.matcher(path);
        if (style.find()) {
            size = Math.max(size, 0);
            path = path.substring(0, style.start()) + "/" + path.substring(style.end());
        }
        Matcher magnolia = MAGNOLIA.matcher(path);
        if (magnolia.find()) {
            String variant = magnolia.group(2).toLowerCase(Locale.ROOT);
            Matcher d = DIGITS.matcher(variant);
            size = Math.max(size, variant.contains("preload") || variant.contains("thumb") ? 50 : d.find() ? num(d.group(1)) : 0);
            path = path.substring(0, magnolia.start()) + "/.imaging/" + path.substring(magnolia.end());
        }

        Matcher cms = TYPO3_PROCESSED.matcher(path);
        if (cms.find()) {
            size = Math.max(size, 0);
            path = path.substring(0, cms.start()) + "/_processed_/csm_" + cms.group(1) + path.substring(cms.end());
        }
        cms = PIMCORE_DIR.matcher(path);
        if (cms.find()) {
            size = Math.max(size, 0);
            path = path.substring(0, cms.start()) + "/image-thumb__" + cms.group(1) + "/" + path.substring(cms.end());
        }
        cms = SEG_CROP_WH.matcher(path);
        if (cms.find()) {
            size = Math.max(size, num(cms.group(2)));
            path = path.substring(0, cms.start()) + cms.group(1) + path.substring(cms.end());
        }
        if (host.endsWith("lemde.fr") && (cms = LEMONDE.matcher(path)).find()) {
            size = Math.max(size, num(cms.group(2)));
            path = cms.group(1) + "/" + path.substring(cms.end());
        }
        if ((host.endsWith("motor1.com") || host.endsWith("motorsport.com") || host.endsWith("insideevs.com"))
                && (cms = MOTOR1_SIZE.matcher(path)).find()) {
            size = Math.max(size, 2400 - 300 * num(cms.group(1)));
            path = path.substring(0, cms.start()) + "/" + path.substring(cms.end());
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

        // 형식만 다른 같은 그림 (picture 의 webp/jpg 짝, photo.jpg.webp): 확장자를 통일해 두면 아래 규칙들이 그대로 맞는다
        path = sameFormat(path);

        // 파일명 앞 썸네일 표시
        Matcher thumb = FILE_THUMB_PREFIX.matcher(path);
        if (thumb.find()) {
            size = Math.max(size, 150);
            path = path.substring(0, thumb.start() + 1) + path.substring(thumb.end());
        }

        // 파일명 안의 크기 표시들 (아래 순서대로 지운다)
        Matcher m = FILE_EASY_THUMB.matcher(path);
        if (m.find()) {
            size = Math.max(size, Math.max(num(m.group(2)), num(m.group(3))));
            path = path.substring(0, m.start()) + m.group(1);
        }
        m = FILE_PARAMS.matcher(path);
        StringBuffer noParams = new StringBuffer();
        while (m.find()) {
            if (m.group(1).equals("resize") || m.group(1).equals("w") || m.group(1).equals("width")) {
                Matcher d = DIGITS.matcher(m.group());
                if (d.find()) size = Math.max(size, num(d.group(1)));
            }
            m.appendReplacement(noParams, "");
        }
        m.appendTail(noParams);
        path = FILE_TRAILING_UNDERSCORE.matcher(noParams.toString()).replaceAll("");
        int retina = 0;
        m = FILE_RETINA.matcher(path);
        if (m.find()) {
            retina = (int) Math.round(Double.parseDouble(m.group(1)));
            path = path.substring(0, m.start()) + path.substring(m.end());
        }
        m = FILE_THUMB_SUFFIX.matcher(path);
        if (m.find()) {
            size = Math.max(size, 150);
            path = path.substring(0, m.start()) + path.substring(m.end());
        }
        m = FILE_WP_EDIT.matcher(path);
        if (m.find()) path = path.substring(0, m.start()) + path.substring(m.end());
        m = FILE_ONLY_WxH.matcher(path);
        if (m.find()) {
            size = Math.max(size, num(m.group(1)));
            path = path.substring(0, m.start()) + "/" + path.substring(m.end());
        }
        Pattern[] widthOnly = {FILE_PIMCORE, FILE_WEBFLOW};
        for (Pattern p : widthOnly) {
            m = p.matcher(path);
            if (m.find()) {
                size = Math.max(size, num(m.group(1)));
                path = path.substring(0, m.start()) + path.substring(m.end());
            }
        }
        m = FILE_HASH_WIDTH.matcher(path);
        if (m.find()) {
            size = Math.max(size, num(m.group(2)));
            path = path.substring(0, m.start()) + m.group(1) + path.substring(m.end());
        }
        m = FILE_OPENCMS.matcher(path);
        if (m.find()) path = path.substring(0, m.start()) + m.group(1) + path.substring(m.end());
        m = FILE_ID_WIDTH_Q.matcher(path);
        if (m.find()) {
            size = Math.max(size, num(m.group(2)));
            path = path.substring(0, m.start()) + m.group(1) + path.substring(m.end());
        }
        m = FILE_WP_SCALED.matcher(path);
        if (m.find()) {
            size = Math.max(size, 2560);
            path = path.substring(0, m.start()) + path.substring(m.end());
        }
        m = FILE_DASH_W.matcher(path);
        if (m.find()) {
            size = Math.max(size, num(m.group(1)));
            path = path.substring(0, m.start()) + path.substring(m.end());
        }
        if (host.endsWith("shopify.com") || path.contains("/cdn/shop/")) {
            m = SHOPIFY_SIZE.matcher(path);
            if (m.find()) {
                String v = m.group(1);
                Integer w = WORD_SIZE.get(v);
                Matcher d = DIGITS.matcher(v);
                size = Math.max(size, v.equals("master") ? ORIGINAL : v.equals("grande") ? 600 : v.equals("compact") ? 160
                        : w != null ? w : d.find() ? num(d.group(1)) : 0);
                path = path.substring(0, m.start()) + path.substring(m.end());
            }
        }
        m = FILE_RDAX.matcher(path);
        if (m.find()) {
            size = Math.max(size, m.group(2) != null ? num(m.group(2)) : 0);
            path = path.substring(0, m.start()) + path.substring(m.end());
        }
        if (host.contains("cnn.") && (m = FILE_CNN.matcher(path)).find()) {
            Integer w = CNN_SIZE.get(m.group(1));
            size = Math.max(size, w != null ? w : 640);
            path = path.substring(0, m.start()) + path.substring(m.end());
        }
        m = FILE_ARD.matcher(path);
        if (m.find()) {
            String v = m.group(1).toLowerCase(Locale.ROOT);
            size = Math.max(size, v.matches(".*(xl|gross|premium|fotogalerie|original).*") ? 1600
                    : v.matches(".*(klein|small|thumb|mini).*") ? 200 : 600);
            path = path.substring(0, m.start()) + path.substring(m.end());
        }
        m = FILE_PYXIS.matcher(path);
        if (m.find()) {
            int w = num(m.group(5));
            size = Math.max(size, m.group(2) != null ? w * num(m.group(2)) : w);
            path = path.substring(0, m.start()) + path.substring(m.end());
        }
        m = FILE_DEVICE.matcher(path);
        if (m.find()) {
            size = Math.max(size, 0);
            path = path.substring(0, m.start()) + path.substring(m.end());
        }
        m = FILE_SIZE_WORD.matcher(path);
        if (m.find() && m.start() > path.lastIndexOf('/') + 1) {
            Integer w = WORD_SIZE.get(m.group(1).toLowerCase(Locale.ROOT));
            size = Math.max(size, m.group(1).equalsIgnoreCase("retina") ? 2000 : w != null ? w : 0);
            path = path.substring(0, m.start()) + path.substring(m.end());
        }
        // 크기(WxH): 겹쳐 붙은 경우(_800x600-750x563.jpg)는 모두 지우고 크기는 마지막(실제 리사이즈) 것
        m = FILE_WxH.matcher(path);
        int lastWidth = -1;
        while (m.find()) {
            lastWidth = Integer.parseInt(m.group(1));
            path = path.substring(0, m.start()) + path.substring(m.end());
            m = FILE_WxH.matcher(path);
        }
        if (lastWidth >= 0) size = Math.max(size, lastWidth);
        m = FILE_NAMED.matcher(path);
        boolean named = m.find();
        if (!named && (host.endsWith("nyt.com") || host.endsWith("nytimes.com"))) {
            m = NYT_NAMED.matcher(path);
            named = m.find();
        }
        if (named) {
            Integer word = WORD_SIZE.get(m.group(1));
            Matcher d = DIGITS.matcher(m.group(1));
            size = Math.max(size, word != null ? word : d.find() ? Integer.parseInt(d.group(1)) : 500);
            path = path.substring(0, m.start()) + path.substring(m.end());
        }

        path = sameFormat(path);   // easy-thumbnails 처럼 지운 뒤 드러난 확장자까지

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
        int finalSize = size < 0 ? ORIGINAL : size == 0 ? ORIGINAL - 1 : size;
        return new Info(key.toString(), finalSize + retina);   // 같은 이름이면 @2x 가 1x 보다 큼
    }


    private static String sameFormat(String path) {
        Matcher m = FILE_FORMAT.matcher(path);
        if (m.find() && m.start() > path.lastIndexOf('/') + 1) return path.substring(0, m.start()) + ".img";
        return path;
    }

    /** 경로 조각이 크기 표시면 그 크기(모르면 0), 아니면 -2 */
    private static int segmentSize(String seg, boolean prevRemoved) {
        if (seg.isEmpty()) return -2;
        String lower = seg.toLowerCase(Locale.ROOT);
        Matcher m;
        if ((m = SEG_WxH.matcher(seg)).matches()) return Math.max(num(m.group(1)), num(m.group(2)));
        if ((m = SEG_XH.matcher(seg)).matches()) return num(m.group(1));
        if ((m = SEG_ZERO_H.matcher(seg)).matches()) return num(m.group(1));
        if (SEG_ASPECT.matcher(seg).matches()) return 0;
        if ((m = SEG_BLOGGER_WH.matcher(seg)).matches()) return num(m.group(1));
        if ((m = SEG_BLOGGER.matcher(seg)).matches()) return m.group(2).equals("0") ? ORIGINAL : num(m.group(2));
        if ((m = SEG_PX.matcher(seg)).matches()) return num(m.group(1));
        if ((m = SEG_DAUM.matcher(seg)).matches()) return num(m.group(1));
        if (SEG_SIGNATURE.matcher(seg).matches()) return 0;
        if (SEG_COLON.matcher(lower).matches()) {
            Matcher d = DIGITS.matcher(seg);
            return d.find() ? num(d.group(1)) : 0;
        }
        if (SIZE_WORDS.contains(lower)) return WORD_SIZE.get(lower);
        if ((m = SEG_UNDERSCORE_WxH.matcher(lower)).matches()) return num(m.group(1));
        // image_full_tab, image_thumb_desk_narrow, mobile: 크기/기기 단어로만 된 조각
        if (lower.indexOf('_') > 0 || lower.indexOf('-') > 0 || SEG_DEVICE.contains(lower)) {
            boolean sizeWord = false, all = true;
            int best = 0;
            for (String t : lower.split("[-_]")) {
                if (SIZE_WORDS.contains(t) && !t.equals("c")) {
                    sizeWord = true;
                    best = Math.max(best, WORD_SIZE.get(t));
                } else if (SEG_DEVICE.contains(t)) {
                    sizeWord = true;
                } else if (!SEG_FILLER.contains(t)) {
                    all = false;
                    break;
                }
            }
            if (all && sizeWord) return best;
        }
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
