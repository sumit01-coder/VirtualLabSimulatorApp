package com.virtuallab.admin.security;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.ui.AppLockActivity;
import com.virtuallab.admin.ui.LoginActivity;

public final class AppLockManager implements Application.ActivityLifecycleCallbacks {
    private int startedCount = 0;
    private static boolean lockActivityLaunching = false;

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        boolean comingToForeground = (startedCount == 0);
        startedCount++;

        if (!comingToForeground) return;
        maybeLock(activity);
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        startedCount = Math.max(0, startedCount - 1);
        if (startedCount != 0) return;
        if (activity.isChangingConfigurations()) return;

        AppLockPrefs.setLastBackgroundAt(activity, System.currentTimeMillis());
    }

    private static void maybeLock(@NonNull Activity activity) {
        Context context = activity.getApplicationContext();
        if (!AppLockPrefs.isEnabled(context)) return;

        // Don't lock login screen or the lock screen itself.
        if (activity instanceof LoginActivity) return;
        if (activity instanceof AppLockActivity) return;

        // Only enforce after a successful login.
        if (!new TokenStore(context).hasToken()) return;

        long bgAt = AppLockPrefs.getLastBackgroundAt(context);
        long unlockedAt = AppLockPrefs.getLastUnlockedAt(context);
        long now = System.currentTimeMillis();

        if (bgAt > 0) {
            if (unlockedAt >= bgAt) return;
            if ((now - bgAt) < AppLockPrefs.DEFAULT_TIMEOUT_MS) return;
        } else {
            // Cold start / unknown background timestamp: lock if last unlock is old/unknown.
            if (unlockedAt > 0 && (now - unlockedAt) < AppLockPrefs.DEFAULT_TIMEOUT_MS) return;
        }

        if (lockActivityLaunching) return;
        lockActivityLaunching = true;

        Intent i = new Intent(activity, AppLockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.startActivity(i);
        activity.overridePendingTransition(0, 0);

        // reset quickly (AppLockActivity will handle actual unlock timestamp)
        lockActivityLaunching = false;
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override public void onActivityResumed(@NonNull Activity activity) {}
    @Override public void onActivityPaused(@NonNull Activity activity) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
    @Override public void onActivityDestroyed(@NonNull Activity activity) {}
}
