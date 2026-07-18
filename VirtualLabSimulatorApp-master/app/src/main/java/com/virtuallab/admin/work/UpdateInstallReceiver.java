package com.virtuallab.admin.work;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;

import com.virtuallab.admin.notifications.NotificationHelper;

public final class UpdateInstallReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_STATUS = "com.virtuallab.admin.UPDATE_INSTALL_STATUS";

    private static final String APP_UPDATE_PREFS = "vl_app_update";
    private static final String KEY_DOWNLOADED_APK_URI = "downloaded_apk_uri";
    private static final String KEY_DOWNLOADED_APK_VERSION = "downloaded_apk_version";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!ACTION_INSTALL_STATUS.equals(intent.getAction())) return;

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                } catch (Exception ignored) {
                }
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            SharedPreferences p = context.getSharedPreferences(APP_UPDATE_PREFS, Context.MODE_PRIVATE);
            p.edit()
                    .remove(KEY_DOWNLOADED_APK_URI)
                    .remove(KEY_DOWNLOADED_APK_VERSION)
                    .apply();
            NotificationHelper.notify(context, 1202, "Updated", "App update installed successfully.");
            
            Intent local = new Intent("com.virtuallab.admin.UPDATE_INSTALL_LOCAL_STATUS");
            local.putExtra("status", status);
            context.sendBroadcast(local);
            return;
        }

        String reason = (message != null && !message.trim().isEmpty()) ? message.trim() : "Install failed";
        String hint = "";
        String lower = reason.toLowerCase();
        if (lower.contains("update_incompatible") || lower.contains("signature") || lower.contains("conflict") || lower.contains("invalid")) {
            hint = " This usually means the APK is signed with a different keystore. Uninstall the old app and install fresh.";
        } else if (lower.contains("version downgrade") || lower.contains("downgrade")) {
            hint = " The APK versionCode is lower than the installed app. Increase versionCode and rebuild.";
        }

        NotificationHelper.notify(context, 1202, "Update failed", reason + hint);

        Intent local = new Intent("com.virtuallab.admin.UPDATE_INSTALL_LOCAL_STATUS");
        local.putExtra("status", status);
        local.putExtra("message", reason + hint);
        context.sendBroadcast(local);
    }
}
