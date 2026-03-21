package com.virtuallab.admin.work;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;

import com.virtuallab.admin.notifications.NotificationHelper;
import com.virtuallab.admin.ui.AppUpdateActivity;

public final class UpdateDownloadReceiver extends BroadcastReceiver {
    private static final String APP_UPDATE_PREFS = "vl_app_update";
    private static final String KEY_LAST_DOWNLOAD_ID = "last_download_id";
    private static final String KEY_LAST_LATEST_VERSION = "last_latest_version";
    private static final String KEY_DOWNLOADED_APK_URI = "downloaded_apk_uri";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;

        long finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        if (finishedId <= 0) return;

        SharedPreferences prefs = context.getSharedPreferences(APP_UPDATE_PREFS, Context.MODE_PRIVATE);
        long expectedId = prefs.getLong(KEY_LAST_DOWNLOAD_ID, -1L);
        if (expectedId <= 0 || expectedId != finishedId) return;

        @Nullable Uri apkUri = null;
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                apkUri = dm.getUriForDownloadedFile(finishedId);
            }
        } catch (Exception ignored) {
        }

        if (apkUri == null) return;

        prefs.edit().putString(KEY_DOWNLOADED_APK_URI, apkUri.toString()).apply();

        String latestVersion = prefs.getString(KEY_LAST_LATEST_VERSION, null);
        String text = (latestVersion != null && !latestVersion.trim().isEmpty())
                ? ("Update " + latestVersion.trim() + " downloaded. Tap to install.")
                : "Update downloaded. Tap to install.";

        Intent ui = new Intent(context, AppUpdateActivity.class);
        ui.putExtra(AppUpdateActivity.EXTRA_AUTO_INSTALL, true);
        ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(context, 1201, ui, flags);

        NotificationHelper.notify(context, 1201, "Update ready", text, pi);
    }
}

