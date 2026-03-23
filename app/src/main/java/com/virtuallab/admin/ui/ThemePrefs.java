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
        int mode = getMode(context);
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode));
    }

    public static int getMode(Context context) {
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int mode = sp.getInt(KEY_MODE, MODE_SYSTEM);
        if (mode != MODE_SYSTEM && mode != MODE_LIGHT && mode != MODE_DARK) return MODE_SYSTEM;
        return mode;
    }

    public static void setMode(Context context, int mode) {
        if (mode != MODE_SYSTEM && mode != MODE_LIGHT && mode != MODE_DARK) mode = MODE_SYSTEM;
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putInt(KEY_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode));
    }

    public static String getModeLabel(Context context) {
        int mode = getMode(context);
        if (mode == MODE_LIGHT) return "Light";
        if (mode == MODE_DARK) return "Dark";
        return "System";
    }

    private static int toNightMode(int mode) {
        if (mode == MODE_LIGHT) return AppCompatDelegate.MODE_NIGHT_NO;
        if (mode == MODE_DARK) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
}

