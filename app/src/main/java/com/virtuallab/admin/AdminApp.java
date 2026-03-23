package com.virtuallab.admin;

import android.app.Application;

import com.virtuallab.admin.security.AppLockManager;
import com.virtuallab.admin.ui.ThemePrefs;

public final class AdminApp extends Application {
    @Override
    public void onCreate() {
        ThemePrefs.apply(this);
        super.onCreate();
        registerActivityLifecycleCallbacks(new AppLockManager());
    }
}

