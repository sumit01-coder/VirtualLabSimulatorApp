package com.virtuallab.admin.ui;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.virtuallab.admin.BuildConfig;
import com.virtuallab.admin.R;
import com.virtuallab.admin.ui.views.EdgeToEdge;

import android.animation.ObjectAnimator;

public final class AppUpdateActivity extends AppCompatActivity {
    public static final String EXTRA_LATEST_VERSION = "latest_version";
    public static final String EXTRA_DOWNLOAD_URL = "download_url";
    public static final String EXTRA_RELEASE_URL = "release_url";
    public static final String EXTRA_NOTES = "notes";
    public static final String EXTRA_PUBLISHED_AT = "published_at";

    private static final String APP_UPDATE_PREFS = "vl_app_update";
    private static final String KEY_LAST_LATEST_VERSION = "last_latest_version";
    private static final String KEY_LAST_DOWNLOAD_URL = "last_download_url";
    private static final String KEY_LAST_RELEASE_URL = "last_release_url";
    private static final String KEY_LAST_NOTES = "last_notes";
    private static final String KEY_LAST_PUBLISHED_AT = "last_published_at";

    private TextView currentVersionText;
    private TextView latestVersionText;
    private TextView publishedAtText;
    private TextView notesText;
    private TextView statusText;

    private MaterialButton downloadBtn;
    private MaterialButton openGithubBtn;

    private ImageView animIcon;
    private CircularProgressIndicator progress;

    private String latestVersion;
    private String downloadUrl;
    private String releaseUrl;
    private String notes;
    private String publishedAt;

    private long downloadId = -1L;
    private ObjectAnimator rotateAnim;
    private BroadcastReceiver downloadReceiver;

    private TextView progressText;
    private Handler progressHandler;
    private Runnable progressRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_update);

        EdgeToEdge.enable(this, findViewById(R.id.root), true, true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("App update");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        currentVersionText = findViewById(R.id.currentVersionText);
        latestVersionText = findViewById(R.id.latestVersionText);
        publishedAtText = findViewById(R.id.publishedAtText);
        notesText = findViewById(R.id.notesText);
        statusText = findViewById(R.id.statusText);

        downloadBtn = findViewById(R.id.downloadBtn);
        openGithubBtn = findViewById(R.id.openGithubBtn);

        animIcon = findViewById(R.id.animIcon);
        progress = findViewById(R.id.progress);
        progressText = findViewById(R.id.progressText);
        progressHandler = new Handler(Looper.getMainLooper());

        if (savedInstanceState != null) {
            downloadId = savedInstanceState.getLong("download_id", -1L);
        }

        loadDataFromIntentOrPrefs();
        bind();

        openGithubBtn.setOnClickListener(v -> openLink(bestReleaseLink()));
        downloadBtn.setOnClickListener(v -> onPrimaryAction());

        if (downloadId > 0) {
            // If activity was recreated, try to restore state.
            maybeAttachReceiver();
            refreshDownloadState();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("download_id", downloadId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopDownloadingUi();
        detachReceiver();
    }

    private void loadDataFromIntentOrPrefs() {
        Intent i = getIntent();

        latestVersion = safeStr(i != null ? i.getStringExtra(EXTRA_LATEST_VERSION) : null);
        downloadUrl = safeStr(i != null ? i.getStringExtra(EXTRA_DOWNLOAD_URL) : null);
        releaseUrl = safeStr(i != null ? i.getStringExtra(EXTRA_RELEASE_URL) : null);
        notes = safeStr(i != null ? i.getStringExtra(EXTRA_NOTES) : null);
        publishedAt = safeStr(i != null ? i.getStringExtra(EXTRA_PUBLISHED_AT) : null);

        if (latestVersion.isEmpty() && downloadUrl.isEmpty() && releaseUrl.isEmpty()) {
            SharedPreferences p = getSharedPreferences(APP_UPDATE_PREFS, Context.MODE_PRIVATE);
            latestVersion = safeStr(p.getString(KEY_LAST_LATEST_VERSION, ""));
            downloadUrl = safeStr(p.getString(KEY_LAST_DOWNLOAD_URL, ""));
            releaseUrl = safeStr(p.getString(KEY_LAST_RELEASE_URL, ""));
            notes = safeStr(p.getString(KEY_LAST_NOTES, ""));
            publishedAt = safeStr(p.getString(KEY_LAST_PUBLISHED_AT, ""));
        }

        if (releaseUrl.isEmpty()) {
            // Fallback: if server provides only one URL, treat it as release link.
            releaseUrl = downloadUrl;
        }
    }

    private void bind() {
        currentVersionText.setText("Current: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");

        if (!latestVersion.isEmpty()) {
            latestVersionText.setText("Latest: " + latestVersion);
        } else {
            latestVersionText.setText("Latest: unknown");
        }

        if (!publishedAt.isEmpty()) {
            publishedAtText.setText("Published: " + publishedAt);
            publishedAtText.setVisibility(View.VISIBLE);
        } else {
            publishedAtText.setVisibility(View.GONE);
        }

        if (!notes.isEmpty()) {
            notesText.setText(notes);
        } else {
            notesText.setText("No release notes.");
        }

        boolean canDownloadInApp = looksLikeApk(downloadUrl);
        downloadBtn.setVisibility(canDownloadInApp ? View.VISIBLE : View.GONE);

        if (bestReleaseLink().isEmpty() && downloadUrl.isEmpty()) {
            openGithubBtn.setEnabled(false);
        }

        statusText.setText(canDownloadInApp ? "Ready to download" : "Open GitHub to download");
    }

    private void onPrimaryAction() {
        if (!looksLikeApk(downloadUrl)) {
            openLink(bestReleaseLink());
            return;
        }

        if (downloadId > 0) {
            refreshDownloadState();
            return;
        }

        startDownload(downloadUrl);
    }

    private void startDownload(String url) {
        if (url == null || url.trim().isEmpty()) {
            toast("No download URL");
            return;
        }

        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            toast("Invalid download URL");
            return;
        }

        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (dm == null) {
            toast("Download service unavailable");
            return;
        }

        String fileName = "virtual-lab-admin" + (!latestVersion.isEmpty() ? ("-v" + latestVersion) : "") + ".apk";

        DownloadManager.Request req = new DownloadManager.Request(uri)
                .setTitle("Virtual Lab Admin")
                .setDescription("Downloading update")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName);

        try {
            downloadId = dm.enqueue(req);
        } catch (Exception e) {
            downloadId = -1L;
            toast("Download failed to start");
            return;
        }

        statusText.setText("Downloading...");
        startDownloadingUi();
        maybeAttachReceiver();
    }

    private void refreshDownloadState() {
        if (downloadId <= 0) return;

        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (dm == null) return;

        DownloadManager.Query q = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor c = dm.query(q)) {
            if (c == null || !c.moveToFirst()) return;

            int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED) {
                if (progress.getVisibility() != View.VISIBLE) startDownloadingUi();
                statusText.setText("Downloading...");
                return;
            }

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                stopDownloadingUi();
                statusText.setText("Downloaded. Tap Install.");
                Uri apkUri = dm.getUriForDownloadedFile(downloadId);
                downloadBtn.setText("Install update");
                downloadBtn.setEnabled(true);
                downloadBtn.setOnClickListener(v -> installApk(apkUri));
                detachReceiver();
                return;
            }

            stopDownloadingUi();
            statusText.setText("Download failed.");
            downloadId = -1L;
            detachReceiver();
        } catch (Exception ignored) {
        }
    }

    private void installApk(@Nullable Uri apkUri) {
        if (apkUri == null) {
            toast("Downloaded file not found");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                toast("Allow 'Install unknown apps' for this app");
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) {
                }
                return;
            }
        }

        try {
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Exception e) {
            toast("Cannot open installer");
        }
    }

    private void maybeAttachReceiver() {
        if (downloadReceiver != null) return;

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (id != downloadId) return;
                refreshDownloadState();
            }
        };

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        } catch (Exception ignored) {
        }
    }

    private void detachReceiver() {
        if (downloadReceiver == null) return;
        try {
            unregisterReceiver(downloadReceiver);
        } catch (Exception ignored) {
        }
        downloadReceiver = null;
    }

    private void startDownloadingUi() {
        progress.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setProgressCompat(0, true);
        downloadBtn.setEnabled(false);

        if (rotateAnim == null) {
            rotateAnim = ObjectAnimator.ofFloat(animIcon, View.ROTATION, 0f, 360f);
            rotateAnim.setDuration(900);
            rotateAnim.setRepeatCount(ObjectAnimator.INFINITE);
            rotateAnim.setInterpolator(new LinearInterpolator());
        }
        if (!rotateAnim.isStarted()) rotateAnim.start();

        if (progressRunnable == null) {
            progressRunnable = new Runnable() {
                @Override
                public void run() {
                    updateProgressFromManager();
                    if (downloadId > 0) {
                        progressHandler.postDelayed(this, 250);
                    }
                }
            };
        }
        progressHandler.post(progressRunnable);
    }

    private void updateProgressFromManager() {
        if (downloadId <= 0) return;
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (dm == null) return;
        try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(downloadId))) {
            if (c != null && c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_RUNNING) {
                    long bytesDownloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long bytesTotal = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    if (bytesTotal > 0) {
                        progress.setIndeterminate(false);
                        int currentProgress = (int) ((bytesDownloaded * 100L) / bytesTotal);
                        progress.setProgressCompat(currentProgress, true);
                        String dlmb = String.format("%.1f", bytesDownloaded / (1024f * 1024f));
                        String totmb = String.format("%.1f", bytesTotal / (1024f * 1024f));
                        progressText.setText(currentProgress + "% (" + dlmb + " MB / " + totmb + " MB)");
                    } else {
                        progress.setIndeterminate(true);
                        progressText.setText("Starting...");
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void stopDownloadingUi() {
        progress.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);
        if (rotateAnim != null) {
            rotateAnim.cancel();
            animIcon.setRotation(0f);
        }
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        downloadBtn.setEnabled(true);
    }

    private String bestReleaseLink() {
        if (releaseUrl != null && !releaseUrl.trim().isEmpty()) return releaseUrl.trim();
        if (downloadUrl != null && !downloadUrl.trim().isEmpty()) return downloadUrl.trim();
        return "";
    }

    private void openLink(String url) {
        if (url == null || url.trim().isEmpty()) {
            toast("No link available");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("Cannot open link");
        }
    }

    private boolean looksLikeApk(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        return true; 
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private String safeStr(String s) {
        return s == null ? "" : s.trim();
    }
}
