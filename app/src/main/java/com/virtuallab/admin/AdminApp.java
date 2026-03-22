package com.virtuallab.admin;

import android.app.Application;

import com.virtuallab.admin.security.AppLockManager;

public final class AdminApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new AppLockManager());
    }
}

