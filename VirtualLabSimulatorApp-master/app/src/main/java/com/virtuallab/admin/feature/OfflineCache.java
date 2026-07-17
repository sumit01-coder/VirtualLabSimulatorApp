package com.virtuallab.admin.feature;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public final class OfflineCache {
    private static final String PREFS = "vl_offline_cache";
    private static final Gson GSON = new Gson();

    private OfflineCache() {}

    public static <T> void putObject(Context context, String key, T value) {
        if (context == null || key == null || key.trim().isEmpty()) return;
        String raw = GSON.toJson(value);
        prefs(context).edit().putString(key, raw).apply();
    }

    public static <T> T getObject(Context context, String key, Class<T> cls) {
        if (context == null || cls == null || key == null || key.trim().isEmpty()) return null;
        String raw = prefs(context).getString(key, null);
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return GSON.fromJson(raw, cls);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static <T> void putList(Context context, String key, List<T> value) {
        putObject(context, key, value);
    }

    public static <T> List<T> getList(Context context, String key, Class<T> cls) {
        if (context == null || key == null || key.trim().isEmpty() || cls == null) return Collections.emptyList();
        String raw = prefs(context).getString(key, null);
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        try {
            Type t = TypeToken.getParameterized(List.class, cls).getType();
            List<T> out = GSON.fromJson(raw, t);
            return out != null ? out : Collections.emptyList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
