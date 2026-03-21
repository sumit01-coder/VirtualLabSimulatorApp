package com.virtuallab.admin.work;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class ApkInstaller {
    private ApkInstaller() {}

    public static boolean installFromUri(Context context, Uri apkUri, @Nullable String expectedPackageName) {
        if (context == null || apkUri == null) return false;

        // Copy to private cache first (DownloadManager Uris can become invalid; this keeps a stable local file).
        File cacheApk = new File(context.getCacheDir(), "vl_update.apk");
        if (!copyToFile(context.getContentResolver(), apkUri, cacheApk)) {
            return false;
        }

        return installFromFile(context, cacheApk, expectedPackageName);
    }

    private static boolean installFromFile(Context context, File apkFile, @Nullable String expectedPackageName) {
        if (!apkFile.exists() || apkFile.length() <= 0) return false;

        try {
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (expectedPackageName != null && !expectedPackageName.trim().isEmpty()) {
                params.setAppPackageName(expectedPackageName.trim());
            }
            int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);

            try (OutputStream out = session.openWrite("base.apk", 0, apkFile.length());
                 InputStream in = new java.io.FileInputStream(apkFile)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
                session.fsync(out);
            }

            Intent statusIntent = new Intent(context, UpdateInstallReceiver.class);
            statusIntent.setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(context, 1202, statusIntent, flags);
            IntentSender sender = pi.getIntentSender();

            session.commit(sender);
            session.close();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean copyToFile(ContentResolver resolver, Uri uri, File outFile) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return false;
            outFile.getParentFile().mkdirs();
            try (OutputStream out = new FileOutputStream(outFile, false)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
            }
            return outFile.exists() && outFile.length() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
