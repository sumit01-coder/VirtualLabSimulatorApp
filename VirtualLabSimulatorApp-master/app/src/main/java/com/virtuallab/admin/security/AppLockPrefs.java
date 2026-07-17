package com.virtuallab.admin.security;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppLockPrefs {
    private static final String PREFS = "vl_security";
    private static final String KEY_ENABLED = "app_lock_enabled";
    private static final String KEY_LAST_UNLOCKED_AT = "last_unlocked_at";
    private static final String KEY_LAST_BACKGROUND_AT = "last_background_at";

    // Default: require unlock if app was in background >= 30 seconds.
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private AppLockPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static long getLastUnlockedAt(Context context) {
        return prefs(context).getLong(KEY_LAST_UNLOCKED_AT, 0L);
    }

    public static void markUnlockedNow(Context context) {
        prefs(context).edit().putLong(KEY_LAST_UNLOCKED_AT, System.currentTimeMillis()).apply();
    }

    public static long getLastBackgroundAt(Context context) {
        return prefs(context).getLong(KEY_LAST_BACKGROUND_AT, 0L);
    }

    public static void setLastBackgroundAt(Context context, long tsMs) {
        prefs(context).edit().putLong(KEY_LAST_BACKGROUND_AT, tsMs).apply();
    }
}

