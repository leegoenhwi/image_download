package com.personal_project.image_download.support;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Locale;

/**
 * 이미지 한 장을 사진/image_download 폴더에 저장한다. (백그라운드 스레드에서 호출)
 * - Android 10 이상: MediaStore 로 저장 (저장 권한 필요 없음, targetSdk 를 올려도 동작)
 * - Android 9 이하: 파일로 저장 후 미디어 스캔 (WRITE_EXTERNAL_STORAGE 필요)
 */
public class ImageSaver {

    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0 Mobile Safari/537.36";

    static final String FOLDER = "image_download";

    public interface Progress {
        void onProgress(int percent);
    }

    /** 로그인/봇 확인 쿠키가 있어야 받아지는 이미지를 위해 WebView 의 쿠키를 넘겨준다. */
    public interface CookieSource {
        String cookieFor(String url);
    }

    public static volatile CookieSource cookies;

    public static String cookieFor(String url) {
        CookieSource c = cookies;
        try {
            return c == null ? null : c.cookieFor(url);
        } catch (Exception e) {
            return null;
        }
    }

    public static File saveDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER);
    }

    /** @return 저장된 위치 (파일 경로 또는 content:// 주소) */
    public static String save(Context context, String imageUrl, String referer, Progress progress) throws Exception {
        if (imageUrl.startsWith("data:")) {
            // data:image/png;base64,.... 형식 (페이지에 박힌 이미지)
            int comma = imageUrl.indexOf(',');
            if (comma < 0) throw new IllegalArgumentException("bad data uri");
            String meta = imageUrl.substring(5, comma);
            String payload = imageUrl.substring(comma + 1);
            byte[] bytes = meta.endsWith(";base64")
                    ? Base64.decode(payload, Base64.DEFAULT)
                    : URLDecoder.decode(payload, "UTF-8").getBytes("UTF-8");
            String mime = meta.split(";")[0];
            return write(context, "inline_image", extension("", mime), mime, new ByteArrayInputStream(bytes), bytes.length, progress);
        }

        HttpURLConnection conn = open(imageUrl, referer);
        try {
            String type = conn.getContentType();
            if (type != null && (type.startsWith("text/") || type.contains("json") || type.contains("javascript"))) {
                // 이미지가 아니라 웹페이지(뷰어/에러 페이지)가 온 경우 저장하지 않는다
                throw new IllegalStateException("not an image: " + type);
            }
            String ext = extension(imageUrl, type);
            InputStream is = conn.getInputStream();
            try {
                return write(context, baseName(imageUrl), ext, mimeOf(ext), is, conn.getContentLength(), progress);
            } finally {
                is.close();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String write(Context context, String name, String ext, String mime, InputStream in, long total,
                                Progress progress) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            return writeMediaStore(context, name, ext, mime, in, total, progress);
        }
        File dir = saveDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create " + dir);
        }
        File file = uniqueFile(dir, name, ext);
        OutputStream out = new FileOutputStream(file);
        boolean ok = false;
        try {
            copy(in, out, total, progress);
            ok = true;
        } finally {
            out.close();
            if (!ok) file.delete();   // 받다가 끊긴 반쪽짜리 파일은 남기지 않는다
        }
        // 갤러리에 바로 보이도록 미디어 스캔
        MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
        return file.getAbsolutePath();
    }

    /** Android 10+ : 사진/image_download (이미지로 등록이 안 되는 형식은 다운로드/image_download) */
    @RequiresApi(29)
    private static String writeMediaStore(Context context, String name, String ext, String mime, InputStream in,
                                          long total, Progress progress) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name + "." + ext);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);   // 다 받기 전에는 갤러리에 안 보이게
        Uri uri = null;
        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (IllegalArgumentException e) {
            // 이미지 모음이 받지 않는 형식 → 아래 다운로드 폴더로
        }
        if (uri == null) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        }
        if (uri == null) throw new IOException("cannot create media entry");
        boolean ok = false;
        try {
            OutputStream out = resolver.openOutputStream(uri);
            if (out == null) throw new IOException("cannot open " + uri);
            try {
                copy(in, out, total, progress);
            } finally {
                out.close();
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            ok = true;
            return uri.toString();
        } finally {
            if (!ok) resolver.delete(uri, null, null);
        }
    }

    private static void copy(InputStream in, OutputStream out, long total, Progress progress) throws IOException {
        byte[] buf = new byte[8192];
        long done = 0;
        int read, lastPercent = -1;
        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
            done += read;
            if (progress != null && total > 0) {
                int percent = (int) Math.min(100, done * 100 / total);
                if (percent != lastPercent) {   // 8KB 마다가 아니라 퍼센트가 바뀔 때만 화면 갱신
                    lastPercent = percent;
                    progress.onProgress(percent);
                }
            }
        }
        // 서버가 알려준 크기보다 적게 받았으면 연결이 끊긴 것 → 깨진 이미지를 저장하지 않도록 실패 처리
        if (total > 0 && done < total) throw new IOException("incomplete download: " + done + "/" + total);
        if (progress != null) progress.onProgress(100);
    }

    static String mimeOf(String ext) {
        if (ext.equals("jpg")) return "image/jpeg";
        if (ext.equals("svg")) return "image/svg+xml";
        if (ext.equals("ico")) return "image/x-icon";
        if (ext.equals("tif")) return "image/tiff";
        return "image/" + ext;
    }

    private static HttpURLConnection open(String url, String referer) throws Exception {
        String current = url;
        for (int i = 0; i < 5; i++) {   // http <-> https 리다이렉트 포함 수동 처리
            HttpURLConnection conn = (HttpURLConnection) new URL(current.replace(" ", "%20")).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "image/*,*/*;q=0.8");
            if (referer != null) conn.setRequestProperty("Referer", referer);
            String cookie = cookieFor(current);
            if (cookie != null && !cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null) throw new IllegalStateException("redirect without location");
                current = new URL(new URL(current), loc).toString();
                continue;
            }
            if (code != 200) {
                conn.disconnect();
                throw new IllegalStateException("HTTP " + code);
            }
            return conn;
        }
        throw new IllegalStateException("too many redirects");
    }

    static String extension(String url, String contentType) {
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.contains("jpeg") || ct.contains("jpg")) return "jpg";
            if (ct.contains("png")) return "png";
            if (ct.contains("gif")) return "gif";
            if (ct.contains("webp")) return "webp";
            if (ct.contains("svg")) return "svg";
            if (ct.contains("bmp")) return "bmp";
            if (ct.contains("avif")) return "avif";
            if (ct.contains("tiff")) return "tif";
            if (ct.contains("icon")) return "ico";
        }
        String path = pathOf(url).toLowerCase(Locale.ROOT);
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && path.length() - dot <= 5) {
            String e = path.substring(dot + 1);
            if (e.matches("[a-z0-9]+")) return e.equals("jpeg") ? "jpg" : e;
        }
        return "jpg";
    }

    static String baseName(String url) {
        String path = pathOf(url);
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        try {
            name = URLDecoder.decode(name, "UTF-8");
        } catch (Exception ignored) {
        }
        name = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (name.length() > 60) name = name.substring(0, 60);
        return name.isEmpty() ? "image" : name;
    }

    private static String pathOf(String url) {
        try {
            String p = new URL(url).getPath();
            return p == null ? "" : p;
        } catch (Exception e) {
            return "";
        }
    }

    private static File uniqueFile(File dir, String base, String ext) {
        File f = new File(dir, base + "." + ext);
        int n = 1;
        while (f.exists()) {
            f = new File(dir, base + "_" + (n++) + "." + ext);
        }
        return f;
    }
}
