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
        };
        for (String[] p : different) {
            assertFalse(p[0] + " vs " + p[1], ImageVariants.key(p[0]).equals(ImageVariants.key(p[1])));
        }
    }
}
