package com.personal_project.image_download.support;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** 최근에 연 주소 (최대 5개, 최신순) */
public final class RecentUrls {

    private static final String PREFS = "recent_urls";
    private static final String KEY = "urls";
    private static final int MAX = 5;

    private RecentUrls() {
    }

    public static List<String> get(Context context) {
        List<String> urls = new ArrayList<String>();
        String saved = prefs(context).getString(KEY, "");
        for (String u : saved.split("\n")) {
            if (!u.isEmpty()) urls.add(u);
        }
        return urls;
    }

    public static void add(Context context, String url) {
        List<String> urls = get(context);
        urls.remove(url);
        urls.add(0, url);
        while (urls.size() > MAX) urls.remove(urls.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String u : urls) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(u);
        }
        prefs(context).edit().putString(KEY, sb.toString()).apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
