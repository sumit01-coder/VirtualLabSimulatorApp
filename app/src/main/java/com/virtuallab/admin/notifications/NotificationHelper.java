package com.virtuallab.admin.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.sumit.virtuallabadmin.v28.R;

public final class NotificationHelper {
    public static final String CHANNEL_ID = "vl_admin_updates";

    private NotificationHelper() {}

    public static void ensureChannel(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
        if (existing != null) return;

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Admin updates",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        ch.setDescription("Notifications for new tickets, new practicals, and app updates.");
        nm.createNotificationChannel(ch);
    }

    public static void notify(Context context, int id, String title, String text) {
        notify(context, id, title, text, null);
    }

    public static void notify(Context context, int id, String title, String text, @Nullable PendingIntent contentIntent) {
        if (context == null) return;
        ensureChannel(context);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_admin_panel_24)
                .setContentTitle(title != null ? title : "Virtual Lab Admin")
                .setContentText(text != null ? text : "")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text != null ? text : ""))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (contentIntent != null) {
            b.setContentIntent(contentIntent);
        }

        NotificationManagerCompat.from(context).notify(id, b.build());
    }
}
