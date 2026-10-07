package com.personal_project.image_download.support;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Locale;

/** 이미지 한 장을 Pictures/image_download 에 저장한다. (백그라운드 스레드에서 호출) */
public class ImageSaver {

    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0 Mobile Safari/537.36";

    public interface Progress {
        void onProgress(int percent);
    }

    public static File saveDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "image_download");
    }

    public static File save(Context context, String imageUrl, String referer, Progress progress) throws Exception {
        File dir = saveDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create " + dir);
        }

        HttpURLConnection conn = open(imageUrl, referer);
        try {
            String type = conn.getContentType();
            if (type != null && (type.startsWith("text/") || type.contains("json") || type.contains("javascript"))) {
                // 이미지가 아니라 웹페이지(뷰어/에러 페이지)가 온 경우 저장하지 않는다
                throw new IllegalStateException("not an image: " + type);
            }
            int total = conn.getContentLength();
            String ext = extension(imageUrl, conn.getContentType());
            File file = uniqueFile(dir, baseName(imageUrl), ext);

            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(file);
            try {
                byte[] buf = new byte[8192];
                long done = 0;
                int read;
                while ((read = is.read(buf)) != -1) {
                    fos.write(buf, 0, read);
                    done += read;
                    if (progress != null && total > 0) {
                        progress.onProgress((int) (done * 100 / total));
                    }
                }
            } finally {
                fos.close();
                is.close();
            }
            if (progress != null) progress.onProgress(100);

            // 갤러리에 바로 보이도록 미디어 스캔
            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
            return file;
        } finally {
            conn.disconnect();
        }
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
