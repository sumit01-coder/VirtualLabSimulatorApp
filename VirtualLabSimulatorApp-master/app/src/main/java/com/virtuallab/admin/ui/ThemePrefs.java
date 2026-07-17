package com.virtuallab.admin.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemePrefs {
    private static final String PREFS = "vl_theme";
    private static final String KEY_MODE = "mode";

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    private ThemePrefs() {}

    public static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static int getMode(Context context) {
        return MODE_LIGHT;
    }

    public static void setMode(Context context, int mode) {
        // Ignored, locked to light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static String getModeLabel(Context context) {
        return "Light";
    }

    private static int toNightMode(int mode) {
        return AppCompatDelegate.MODE_NIGHT_NO;
    }
}

