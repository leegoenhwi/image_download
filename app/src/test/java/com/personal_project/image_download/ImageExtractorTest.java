package com.personal_project.image_download;

import com.personal_project.image_download.support.ImageExtractor;
import com.personal_project.image_download.support.ImageVariants;

import org.jsoup.Jsoup;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 이미지 추출 규칙 테스트. 각 HTML 조각은 실제 사이트에서 확인한 구조를 줄인 것.
 * (./gradlew test 로 기기 없이 실행)
 */
public class ImageExtractorTest {

    private static final String BASE = "https://example.com/news/article.html";

    private static List<String> extract(String html) {
        Set<String> found = ImageExtractor.extract(Jsoup.parse(html, BASE), BASE);
        return new ArrayList<String>(found);
    }

    @Test
    public void srcsetPicksLargestCandidateOnly() {
        List<String> r = extract("<img src='/a_300.jpg' srcset='/a_300.jpg 300w,/a_1200.jpg 1200w, /a_600.jpg 600w'>");
        assertEquals(Arrays.asList("https://example.com/a_1200.jpg"), r);
    }

    @Test
    public void srcsetWithUrlCommasAndNoSpaces() {
        List<String> r = extract("<img srcset='https://cdn.x.com/upload/w_400,c_fill/p.jpg 400w,https://cdn.x.com/upload/w_900,c_fill/p.jpg 900w'>");
        assertEquals(Arrays.asList("https://cdn.x.com/upload/w_900,c_fill/p.jpg"), r);
    }

    @Test
    public void lazySrcsetBehindDataUriPlaceholder() {
        // lazysizes: srcset 에는 투명 gif, 진짜 주소는 data-srcset
        List<String> r = extract("<img src='data:image/gif;base64,R0lGOD' srcset='data:image/gif;base64,R0lGOD' "
                + "data-srcset='/real-480.jpg 480w, /real-960.jpg 960w'>");
        assertEquals(Arrays.asList("https://example.com/real-960.jpg"), r);
    }

    @Test
    public void lazyAttributesBeatPlaceholderSrc() {
        List<String> r = extract("<img src='/static/placeholder.gif' data-src='/photos/1.jpg'>"
                + "<img src='/lazy.png' data-lazyload='/photos/2.jpg'>"
                + "<img src='/thumb.jpg' data-zoom-image='/photos/3-big.jpg'>");
        assertEquals(Arrays.asList("https://example.com/photos/1.jpg", "https://example.com/photos/2.jpg",
                "https://example.com/photos/3-big.jpg"), r);
    }

    @Test
    public void idAttributeIsNotTreatedAsUrl() {
        // Medium: data-image-id 값은 파일명이지만 주소가 아님 → 진짜 src 를 잃으면 안 된다
        List<String> r = extract("<img data-image-id='1*abc.png' data-width='1200' src='https://cdn.medium.com/max/800/1*abc.png'>");
        assertEquals(Arrays.asList("https://cdn.medium.com/max/800/1*abc.png"), r);
    }

    @Test
    public void jsonInsideLazyAttribute() {
        // MSN: data-src 안에 JSON
        List<String> r = extract("<img data-src='{\"default\":{\"src\":\"//img.cdn.net/entity/AAkk5.img?h=410&w=728\"}}'>");
        assertEquals(Arrays.asList("https://img.cdn.net/entity/AAkk5.img?h=410&w=728"), r);
    }

    @Test
    public void linkedOriginalBeatsResizedThumbnail() {
        // WordPress: 원본으로 링크된 리사이즈 이미지 → 원본 하나만
        List<String> r = extract("<a href='/wp-content/uploads/photo.png'><img src='/wp-content/uploads/photo-300x200.png' "
                + "srcset='/wp-content/uploads/photo-300x200.png 300w, /wp-content/uploads/photo-1024x683.png 1024w'></a>");
        assertEquals(Arrays.asList("https://example.com/wp-content/uploads/photo.png"), r);
    }

    @Test
    public void wikiFilePageLinkIsNotAnImage() {
        List<String> r = extract("<a href='/wiki/File:Cat.jpg'><img src='//upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Cat.jpg/220px-Cat.jpg' "
                + "srcset='//upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Cat.jpg/440px-Cat.jpg 2x' width='220'></a>");
        assertEquals(Arrays.asList("https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Cat.jpg/440px-Cat.jpg"), r);
    }

    @Test
    public void pictureSourcesReplaceInnerImgThumbnail() {
        List<String> r = extract("<picture><source type='image/webp' srcset='/a_480.webp 480w, /a_960.webp 960w'>"
                + "<img src='/a_480.jpg'></picture>");
        assertEquals(Arrays.asList("https://example.com/a_960.webp"), r);
    }

    @Test
    public void tinyIconsAndTrackersAreDropped() {
        List<String> r = extract("<img src='/icons/edit.png' width='16' height='16'>"
                + "<img src='/beacon/1x1.gif'>"
                + "<img src='https://sb.scorecardresearch.com/p?c1=2'>"
                + "<img src='/banners/ad_300x250.jpg'>"
                + "<link rel='apple-touch-icon' href='/apple-touch-icon.png'>"
                + "<img src='/photos/real.jpg'>");
        assertEquals(Arrays.asList("https://example.com/photos/real.jpg"), r);
    }

    @Test
    public void spriteSheetsDroppedButNotPhotosNamedSprite() {
        List<String> r = extract("<img src='/assets/sprites/icons-s48f.png'><img src='/img/icons-sprite@2x.png'>"
                + "<img src='/img/bubbleSprite_3.png'><img src='/img/rv_mini_sprites_v2.png'><img src='/img/slogos_sprite8.png'>"
                + "<img src='/photos/sprite-can-ad-campaign.jpg'>");
        assertEquals(Arrays.asList("https://example.com/photos/sprite-can-ad-campaign.jpg"), r);
    }

    @Test
    public void wikimediaAdFolderIsNotAnAd() {
        // 해시 폴더 /a/ad/ 를 광고 경로로 오인하면 안 된다
        List<String> r = extract("<img src='https://upload.wikimedia.org/wikipedia/commons/a/ad/Queen.jpg'>");
        assertEquals(1, r.size());
    }

    @Test
    public void logosAndIconsGoToTheEnd() {
        List<String> r = extract("<img src='/img/site-logo.png'><img src='/photos/a.jpg'><img src='/img/share-facebook.png'><img src='/photos/b.jpg'>");
        assertEquals(Arrays.asList("https://example.com/photos/a.jpg", "https://example.com/photos/b.jpg",
                "https://example.com/img/site-logo.png", "https://example.com/img/share-facebook.png"), r);
    }

    @Test
    public void urlsWithPipesAndNonAsciiNamesAreKept() {
        // Wagtail 렌더 주소(| 포함)는 java.net.URI 가 거부하던 것, 움라우트/한글 파일명은 브라우저처럼 %XX 로
        List<String> r = extract("<img src='https://cms.example.net/images/3417/fill-897x598|format-webp/'>"
                + "<img src='/uploads/riksgr\u00e4nsen.jpg'><img src='/uploads/\uad81-\ud3ec\uc2a4\ud130.jpg'>");
        assertEquals(Arrays.asList("https://cms.example.net/images/3417/fill-897x598|format-webp/",
                "https://example.com/uploads/riksgr%C3%A4nsen.jpg",
                "https://example.com/uploads/%EA%B6%81-%ED%8F%AC%EC%8A%A4%ED%84%B0.jpg"), r);
    }

    @Test
    public void pageLinksInDataAttributesAreNotImages() {
        // 공유 버튼/캐러셀이 쓰는 data-pin-url, data-slide-url, data-url 은 페이지 주소
        List<String> r = extract("<img data-pin-url='https://example.com/recipe/' data-lazy-src='/photos/a.jpg'>"
                + "<img data-url='/archiv/ausgabe-3/' src='/covers/3.jpg'>"
                + "<img data-slide-url='https://example.com/gallery/slide/2/' src='/photos/b.jpg'>"
                + "<div data-image-height='1500px//cdn.example.com/c_5000x.jpg'></div>");
        assertEquals(Arrays.asList("https://example.com/photos/a.jpg", "https://example.com/covers/3.jpg",
                "https://example.com/photos/b.jpg"), r);
    }

    @Test
    public void lazyLoadingPlaceholdersAreDropped() {
        List<String> r = extract("<img src='https://cdn.example.net/1px/lgray.jpg' data-src='/p/1.jpg'>"
                + "<img src='/i2/preloading.gif'><img src='/wp-content/uploads/1x1-f7f7f7ff.png'>"
                + "<img src='/img/leer.gif'><img src='/t.gif'><img src='/res/lazyload-transparent-image-data.gif'>"
                + "<img src='/custom/share/placeholder_0.jpg'><img src='/images/photo-{{size}}.jpg'>"
                + "<img src='https://counter.yadro.ru/hit?t42'><img src='/p/2.jpg'>");
        assertEquals(Arrays.asList("https://example.com/p/1.jpg", "https://example.com/p/2.jpg"), r);
    }

    @Test
    public void encodeUrlLeavesHostAndEscapesAlone() {
        assertEquals("https://a.de/Gr%C3%B6%C3%9Fe%201.jpg?q=%C3%A4", ImageVariants.encodeUrl("https://a.de/Gr\u00f6\u00dfe 1.jpg?q=\u00e4"));
        assertEquals("https://a.de/x%C3%A4.jpg", ImageVariants.encodeUrl("https://a.de/x%C3%A4.jpg"));
        assertEquals("http://\ud55c\uae00.com/%EC%82%AC.jpg", ImageVariants.encodeUrl("http://\ud55c\uae00.com/\uc0ac.jpg"));
    }

    @Test
    public void metadataImages() {
        List<String> r = extract("<head><meta property='og:image' content='/og.jpg'>"
                + "<meta name='twitter:image' content='/og.jpg'>"
                + "<script type='application/ld+json'>{\"@type\":\"NewsArticle\",\"image\":{\"@type\":\"ImageObject\","
                + "\"url\":\"https://example.com/ld-image.jpg\",\"width\":{\"value\":1200}}}</script></head>"
                + "<figure itemtype='http://schema.org/ImageObject' itemid='https://example.com/figure.jpg'></figure>");
        assertTrue(r.contains("https://example.com/og.jpg"));
        assertTrue(r.contains("https://example.com/ld-image.jpg"));
        assertTrue(r.contains("https://example.com/figure.jpg"));
        assertEquals(3, r.size());
    }

    @Test
    public void baseHrefAndCssBackgrounds() {
        List<String> r = extract("<head><base href='/shop/sub/'><style>.hero{background:url(../../img/hero.png)} "
                + "@font-face{src:url(/f.woff2)}</style></head><div style=\"background-image:url('p1.jpg')\"></div>");
        assertTrue(r.contains("https://example.com/img/hero.png"));
        assertTrue(r.contains("https://example.com/shop/sub/p1.jpg"));
        assertEquals(2, r.size());
    }

    @Test
    public void inlineBase64OnlyWhenLargeEnough() {
        StringBuilder big = new StringBuilder("data:image/png;base64,");
        for (int i = 0; i < 3000; i++) big.append('A');
        List<String> r = extract("<img src='" + big + "'><img src='data:image/gif;base64,R0lGODlhAQABAAAAACw='>");
        assertEquals(1, r.size());
        assertTrue(r.get(0).startsWith("data:image/png"));
    }

    @Test
    public void normalizeUrlFromSharedText() {
        assertEquals("https://m.blog.naver.com/abc/223456789",
                ImageExtractor.normalizeUrl("[네이버 블로그] 제주 여행\nhttps://m.blog.naver.com/abc/223456789"));
        assertEquals("https://n.news.naver.com/article/001/1?sid=100",
                ImageExtractor.normalizeUrl("이 기사 https://n.news.naver.com/article/001/1?sid=100 (연합뉴스)"));
        assertEquals("https://example.com/gallery", ImageExtractor.normalizeUrl("example.com/gallery"));
        assertNull(ImageExtractor.normalizeUrl("그냥 글자"));
        assertNull(ImageExtractor.normalizeUrl("ftp://files.example.com/a.jpg"));
    }

    @Test
    public void isMinor() {
        assertTrue(ImageExtractor.isMinor("https://x.com/img/logo.svg"));
        assertFalse(ImageExtractor.isMinor("https://x.com/photos/beach.jpg"));
    }

    @Test
    public void variantsOfTheSameImageShareAKey() {
        String[][] same = {
                {"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Cat.jpg/220px-Cat.jpg", "https://upload.wikimedia.org/wikipedia/commons/a/ad/Cat.jpg"},
                {"https://site.com/wp-content/uploads/photo-1024x683.jpg", "https://site.com/wp-content/uploads/photo.jpg"},
                {"https://1.bp.blogspot.com/-abc/XYZ/s640/photo.png", "https://1.bp.blogspot.com/-abc/XYZ/s1600/photo.png"},
                {"https://i.guim.co.uk/img/media/abc/master/4800.jpg?width=300&quality=85&s=1", "https://i.guim.co.uk/img/media/abc/master/4800.jpg?width=1300&s=2"},
                {"https://res.cloudinary.com/x/image/upload/w_400,c_fill/p.jpg", "https://res.cloudinary.com/x/image/upload/p.jpg"},
                {"https://img1.daumcdn.net/thumb/R750x0/?fname=http%3A%2F%2Ft1.daumcdn.net%2Fa.jpg", "https://img1.daumcdn.net/thumb/R1280x0/?fname=http%3A%2F%2Ft1.daumcdn.net%2Fa.jpg"},
                {"https://blogfiles.pstatic.net/a/photo.jpg?type=w80_blur", "https://blogfiles.pstatic.net/a/photo.jpg?type=w966"},
                {"https://static01.nyt.com/images/2019/a/merlin_1-articleLarge.jpg?quality=75", "https://static01.nyt.com/images/2019/a/merlin_1-superJumbo.jpg"},
                {"https://media.site.com/photos/1/master/w_120,c_limit/p.jpg", "https://media.site.com/photos/1/master/w_1280,c_limit/p.jpg?mbid=social_retweet"},
                {"https://t2.genius.com/unsafe/128x128/https%3A%2F%2Fimages.genius.com%2Fabc.1000x1000x1.jpg", "https://images.genius.com/abc.1000x1000x1.jpg"},
                {"https://resizer.glanacion.com/resizer/Ys2lyOgzyBq70esAgpOnDTO7gU4=/233x159/smart/filters:format(webp):quality(80)/cloudfront-us-east-1.images.arcpublishing.com/lanacionar/YOI.jpg",
                        "https://resizer.glanacion.com/resizer/fOL-wZWg5VKZS0vQNAMZorTv5Rk=/1200x800/smart/filters:format(webp):quality(80)/cloudfront-us-east-1.images.arcpublishing.com/lanacionar/YOI.jpg"},
                {"http://site.de/wp-content/gallery/lions/thumbs/thumbs_lion-0030.jpg", "http://site.de/wp-content/gallery/lions/lion-0030.jpg"},
                {"https://static01.nyt.com/images/2022/01/23/opinion/20kukla/20kukla-mediumThreeByTwo440.jpg", "https://static01.nyt.com/images/2022/01/23/opinion/20kukla/20kukla-videoLarge.jpg"},
                {"http://cdn.xsd.cz/resize/9b6b_extract=67,0,1867,1050_resize=680,383_.jpg?hash=a5d8", "http://cdn.xsd.cz/resize/9b6b_extract=67,0,1867,1050_resize=1360,765_.jpg?hash=0a51"},
                {"https://chabermu.files.wordpress.com/2015/09/imga131_thumb1.png?w=320&h=160", "https://chabermu.files.wordpress.com/2015/09/imga131.png"},
                {"https://correctiv.org/media/thumbs/filer_public/16/b7/shot.png__768x0_q85_subject_location-911%2C422.png", "https://correctiv.org/media/thumbs/filer_public/16/b7/shot.png__1140x0_q85_subject_location-911%2C422.png"},
                {"http://site.com/t/assets/i/curtschilling-sidebar.jpg", "http://site.com/t/assets/i/curtschilling-sidebar@2x.jpg"},
                {"https://site.com/header/Infrastructure-ATF-375x150-light.png", "https://site.com/header/Infrastructure-ATF-1024x250-light.png"},
                {"https://i.guim.co.uk/img/media/abc/master/1200.jpg?width=300&amp;quality=85&amp;s=1", "https://i.guim.co.uk/img/media/abc/master/1200.jpg?width=1200&s=2"},
                // 형식만 다른 짝 (picture 의 webp/jpg, webp-express)
                {"https://site.com/wp-content/uploads/2022/01/a-300x200.jpg.webp", "https://site.com/wp-content/uploads/2022/01/a.jpg"},
                {"https://site.com/wp-content/webp-express/webp-images/doc-root/wp-content/uploads/Z-1020x680.jpg.webp", "https://site.com/wp-content/uploads/Z.jpg"},
                // Timber, 크롭 표시, Drupal 스타일, TYPO3, Pimcore, Webflow
                {"https://p.co.uk/u/2022/07/cover_v2-140x0-c-default.webp", "https://p.co.uk/u/2022/07/cover_v2-280x0-c-default.jpg"},
                {"https://www.furche.at/images/content/3195813-310x207c-Bolsonaro.jpg", "https://www.furche.at/images/content/3195813-345x230c-Bolsonaro.jpg"},
                {"https://site.de/sites/default/files/styles/article_main_small/public/media/x.jpg", "https://site.de/sites/default/files/media/x.jpg"},
                {"https://www.oetker.de/cms/image-thumb__4830__MainNavImage/lupe~-~480w.png", "https://www.oetker.de/cms/image-thumb__4830__MainNavImage/lupe~-~768w.png"},
                {"https://uploads-ssl.webflow.com/5e99/5ef0_banner-p-500.png", "https://uploads-ssl.webflow.com/5e99/5ef0_banner-p-1080.png"},
                // 숫자 너비/품질, 크기 단어, 기기별, CNN, 르몽드, 워드프레스 -scaled
                {"https://cdn.mos.cms.futurecdn.net/yqCRxXKBQFsko9GVPiMYuE-320-80.jpg", "https://cdn.mos.cms.futurecdn.net/yqCRxXKBQFsko9GVPiMYuE-1280-80.png.webp"},
                {"https://s.hdnux.com/photos/01/18636689/15/gallery_medium.jpg", "https://s.hdnux.com/photos/01/18636689/15/gallery_xlarge.jpg"},
                {"https://cdn.cnn.com/cnnnext/dam/assets/2103-harry-small-169.jpg", "https://cdn.cnn.com/cnnnext/dam/assets/2103-harry-exlarge-169.jpg"},
                {"https://img.lemde.fr/2022/07/18/3/0/6016/4010/180/0/95/0/1b1f4cf_q.jpg", "https://img.lemde.fr/2022/07/18/3/0/6016/4010/360/0/95/0/1b1f4cf_q.jpg"},
                {"https://t.de/uploads/2020/08/baby-33-683x1024.jpg", "https://t.de/uploads/2020/08/baby-33-scaled.jpg"},
                {"https://pyxis.nymag.com/v1/imgs/bd3/2e4d-Midterm.rsquare.w536.jpg", "https://pyxis.nymag.com/v1/imgs/bd3/2e4d-Midterm.2x.rhorizontal.w420.jpg"},
                {"https://media-cdn.sueddeutsche.de/image/sz.1.4433597/200x150?v=1557125214000", "https://media-cdn.sueddeutsche.de/image/sz.1.4433597/400x300?v=1557125214000"},
                // 한글/움라우트: 날것과 %XX 표기는 같은 그림
                {"http://x.kr/data/%EA%B6%81-450x641.jpg", "http://x.kr/data/\uad81.jpg"},
        };
        for (String[] p : same) {
            assertEquals(p[0] + " vs " + p[1], ImageVariants.key(p[0]), ImageVariants.key(p[1]));
            assertTrue("bigger: " + p[1], ImageVariants.isBigger(p[1], p[0]));
        }
    }

    @Test
    public void differentImagesKeepDifferentKeys() {
        String[][] different = {
                {"https://site.com/media-library/image.jpg?id=30813832&width=1200", "https://site.com/media-library/image.jpg?id=30813912&width=1200"},
                {"https://images.site.com/resize?width=370&url=https://cdn.x.com/a.jpg", "https://images.site.com/resize?width=370&url=https://cdn.x.com/b.jpg"},
                {"https://site.com/photos/123/original.jpg", "https://site.com/photos/456/original.jpg"},
                {"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Cat.jpg/220px-Cat.jpg", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/Dog.jpg/220px-Dog.jpg"},
                {"https://site.com/header/Infrastructure-ATF-375x150-light.png", "https://site.com/header/Infrastructure-ATF-375x150-dark.png"},
                {"https://site.com/photo-final.jpg", "https://site.com/photo.jpg"},
                {"https://example.org/img/teamPhoto.jpg", "https://example.org/img/team.jpg"},
                {"https://a.com/img/anim.gif", "https://a.com/img/anim.jpg"},
                {"https://a.com/img/IMG-1234.jpg", "https://a.com/img/IMG-1235.jpg"},
                {"https://a.com/2019_10/photo.jpg", "https://a.com/2019_11/photo.jpg"},
                {"https://www.site.de/images/banner/Garmin_120x600px2.jpg", "https://www.site.de/images/banner/Garmin_120x600px3.jpg"},
                {"https://d111abc.cloudfront.net/logo.png", "https://d222abc.cloudfront.net/logo.png"},
                {"https://www.slf.ch/fileadmin/_processed_/b/e/csm_A_f3cc1d28fb.jpg", "https://www.slf.ch/fileadmin/_processed_/b/e/csm_B_f3cc1d28fb.jpg"},
                {"https://cdn.shopify.com/s/files/1/products/a_400x.jpg", "https://cdn.shopify.com/s/files/1/products/b_400x.jpg"},
        };
        for (String[] p : different) {
            assertFalse(p[0] + " vs " + p[1], ImageVariants.key(p[0]).equals(ImageVariants.key(p[1])));
        }
    }
}
