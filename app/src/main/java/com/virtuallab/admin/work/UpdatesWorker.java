package com.virtuallab.admin.work;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.virtuallab.admin.BuildConfig;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.AppUpdateData;
import com.virtuallab.admin.model.UpdatesData;
import com.virtuallab.admin.notifications.NotificationHelper;
import com.virtuallab.admin.ui.AppUpdateActivity;

import retrofit2.Response;

public final class UpdatesWorker extends Worker {
    private static final String PREFS = "vl_updates";
    private static final String KEY_LAST_TICKET_ID = "last_ticket_id";
    private static final String KEY_LAST_PRACTICAL_ID = "last_practical_id";
    private static final String KEY_LAST_MAINTENANCE = "last_maintenance";

    private static final String APP_UPDATE_PREFS = "vl_app_update";
    private static final String KEY_AUTO_CHECK = "auto_check";
    private static final String KEY_LAST_CHECKED_AT = "last_checked_at";
    private static final String KEY_LAST_LATEST_VERSION = "last_latest_version";
    private static final String KEY_LAST_DOWNLOAD_URL = "last_download_url";
    private static final String KEY_LAST_RELEASE_URL = "last_release_url";
    private static final String KEY_LAST_NOTES = "last_notes";
    private static final String KEY_LAST_PUBLISHED_AT = "last_published_at";
    private static final String KEY_LAST_NOTIFIED_VERSION = "last_notified_version";

    private static final long APP_CHECK_MIN_INTERVAL_MS = 6L * 60L * 60L * 1000L; // 6 hours

    public UpdatesWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        TokenStore store = new TokenStore(context);
        if (!store.hasToken()) return Result.success();

        ApiService api = ApiClient.get(store);

        try {
            Response<ApiResponse<UpdatesData>> r = api.updates().execute();
            if (!r.isSuccessful() || r.body() == null || !r.body().status || r.body().data == null) {
                // Continue to app update check even if updates.php failed.
            }

            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            int lastTicketId = prefs.getInt(KEY_LAST_TICKET_ID, 0);
            int lastPracticalId = prefs.getInt(KEY_LAST_PRACTICAL_ID, 0);
            int lastMaintenance = prefs.getInt(KEY_LAST_MAINTENANCE, -1); // -1 = unknown

            if (r.isSuccessful() && r.body() != null && r.body().status && r.body().data != null) {
                UpdatesData data = r.body().data;
                int newestTicketId = data.latest_ticket != null ? data.latest_ticket.id : 0;
                int newestPracticalId = data.latest_practical != null ? data.latest_practical.id : 0;
                boolean maintenanceMode = data.maintenance_mode;

                // First run: just seed, don't spam.
                if (lastTicketId == 0 && newestTicketId > 0) lastTicketId = newestTicketId;
                if (lastPracticalId == 0 && newestPracticalId > 0) lastPracticalId = newestPracticalId;
                if (lastMaintenance == -1) lastMaintenance = maintenanceMode ? 1 : 0;

                if (newestTicketId > lastTicketId) {
                    String subject = data.latest_ticket.subject != null ? data.latest_ticket.subject : "New support ticket";
                    NotificationHelper.notify(context, 1001, "New ticket", subject);
                    lastTicketId = newestTicketId;
                }

                if (newestPracticalId > lastPracticalId) {
                    String title = data.latest_practical.title != null ? data.latest_practical.title : "New practical added";
                    NotificationHelper.notify(context, 1002, "New practical", title);
                    lastPracticalId = newestPracticalId;
                }

                if (lastMaintenance == 0 && maintenanceMode) {
                    NotificationHelper.notify(context, 1003, "Maintenance mode", "Maintenance mode is now ON.");
                    lastMaintenance = 1;
                } else if (!maintenanceMode) {
                    lastMaintenance = 0;
                }
            }

            prefs.edit()
                    .putInt(KEY_LAST_TICKET_ID, lastTicketId)
                    .putInt(KEY_LAST_PRACTICAL_ID, lastPracticalId)
                    .putInt(KEY_LAST_MAINTENANCE, lastMaintenance)
                    .apply();

            // App update check (optional)
            SharedPreferences appPrefs = context.getSharedPreferences(APP_UPDATE_PREFS, Context.MODE_PRIVATE);
            boolean autoCheck = appPrefs.getBoolean(KEY_AUTO_CHECK, true);
            long lastCheckedAt = appPrefs.getLong(KEY_LAST_CHECKED_AT, 0L);
            long now = System.currentTimeMillis();
            if (autoCheck && (now - lastCheckedAt) >= APP_CHECK_MIN_INTERVAL_MS) {
                Response<ApiResponse<AppUpdateData>> ur = api.appUpdate("android", BuildConfig.VERSION_NAME).execute();
                if (ur.isSuccessful() && ur.body() != null && ur.body().status && ur.body().data != null) {
                    AppUpdateData d = ur.body().data;
                    String latestVer = (d.latest != null) ? d.latest.version : null;
                    String downloadUrl = (d.latest != null) ? d.latest.download_url : null;
                    String releaseUrl = (d.latest != null) ? d.latest.release_url : null;
                    String notes = (d.latest != null) ? d.latest.notes : null;
                    String publishedAt = (d.latest != null) ? d.latest.published_at : null;

                    boolean available = d.update_available && latestVer != null && !latestVer.trim().isEmpty();

                    String lastNotified = appPrefs.getString(KEY_LAST_NOTIFIED_VERSION, null);
                    if (available && (lastNotified == null || !latestVer.equals(lastNotified))) {
                        Intent ui = new Intent(context, AppUpdateActivity.class);
                        ui.putExtra(AppUpdateActivity.EXTRA_LATEST_VERSION, latestVer);
                        ui.putExtra(AppUpdateActivity.EXTRA_DOWNLOAD_URL, downloadUrl);
                        ui.putExtra(AppUpdateActivity.EXTRA_RELEASE_URL, releaseUrl);
                        ui.putExtra(AppUpdateActivity.EXTRA_NOTES, notes);
                        ui.putExtra(AppUpdateActivity.EXTRA_PUBLISHED_AT, publishedAt);

                        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            flags |= PendingIntent.FLAG_IMMUTABLE;
                        }
                        PendingIntent pi = PendingIntent.getActivity(context, 1100, ui, flags);

                        NotificationHelper.notify(
                                context,
                                1100,
                                "App update available",
                                "New version " + latestVer + " is available. Tap to update.",
                                pi
                        );
                        appPrefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, latestVer).apply();
                    }

                    appPrefs.edit()
                            .putLong(KEY_LAST_CHECKED_AT, now)
                            .putString(KEY_LAST_LATEST_VERSION, latestVer)
                            .putString(KEY_LAST_DOWNLOAD_URL, downloadUrl)
                            .putString(KEY_LAST_RELEASE_URL, releaseUrl)
                            .putString(KEY_LAST_NOTES, notes)
                            .putString(KEY_LAST_PUBLISHED_AT, publishedAt)
                            .apply();
                } else {
                    appPrefs.edit().putLong(KEY_LAST_CHECKED_AT, now).apply();
                }
            }

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
