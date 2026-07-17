package com.virtuallab.admin.feature;

import android.content.Context;
import android.content.SharedPreferences;

public final class FeaturePrefs {
    private static final String PREFS = "vl_features";
    private static final String KEY_REALTIME_ALERTS = "realtime_alerts";
    private static final String KEY_REMOTE_CONFIG_URL = "remote_config_url";
    private static final String KEY_DDOS_PRESET = "ddos_preset";

    private FeaturePrefs() {}

    public static boolean isRealtimeAlertsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_REALTIME_ALERTS, true);
    }

    public static void setRealtimeAlertsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_REALTIME_ALERTS, enabled).apply();
    }

    public static String getRemoteConfigUrl(Context context, String fallback) {
        String v = prefs(context).getString(KEY_REMOTE_CONFIG_URL, null);
        if (v == null || v.trim().isEmpty()) return fallback;
        return v.trim();
    }

    public static void setRemoteConfigUrl(Context context, String url) {
        prefs(context).edit().putString(KEY_REMOTE_CONFIG_URL, url != null ? url.trim() : "").apply();
    }

    public static String getDdosPreset(Context context) {
        return prefs(context).getString(KEY_DDOS_PRESET, "normal");
    }

    public static void setDdosPreset(Context context, String preset) {
        prefs(context).edit().putString(KEY_DDOS_PRESET, preset != null ? preset.trim() : "normal").apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
