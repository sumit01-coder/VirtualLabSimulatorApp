package com.virtuallab.admin.ui.fragments;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.virtuallab.admin.BuildConfig;
import com.virtuallab.admin.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.AppUpdateData;
import com.virtuallab.admin.model.SettingsData;
import com.virtuallab.admin.model.SettingsUpdateRequest;
import com.virtuallab.admin.notifications.NotificationHelper;
import com.virtuallab.admin.security.AppLockPrefs;
import com.virtuallab.admin.security.AppLockSupport;
import com.virtuallab.admin.ui.LabsActivity;
import com.virtuallab.admin.ui.DepartmentsActivity;
import com.virtuallab.admin.ui.AppUpdateActivity;
import com.virtuallab.admin.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class SettingsFragment extends BaseAuthedFragment {
    private static final int NOTIFICATION_ID_SETTINGS = 1201;

    private static final String APP_UPDATE_PREFS = "vl_app_update";
    private static final String KEY_AUTO_CHECK = "auto_check";
    private static final String KEY_LAST_CHECKED_AT = "last_checked_at";
    private static final String KEY_LAST_LATEST_VERSION = "last_latest_version";
    private static final String KEY_LAST_DOWNLOAD_URL = "last_download_url";
    private static final String KEY_LAST_RELEASE_URL = "last_release_url";
    private static final String KEY_LAST_NOTES = "last_notes";
    private static final String KEY_LAST_PUBLISHED_AT = "last_published_at";
    private static final String KEY_LAST_UPDATE_AVAILABLE = "last_update_available";

    private ApiService api;
    private TokenStore store;
    private SwipeRefreshLayout swipe;
    private TextView accessHint;
    private SwitchMaterial maintenanceSwitch;
    private SwitchMaterial admin2faSwitch;
    private SwitchMaterial appLockSwitch;
    private MaterialButton saveBtn;
    private MaterialButton manageLabsBtn;
    private MaterialButton manageDepartmentsBtn;

    private TextView appVersionText;
    private TextView appUpdateStatusText;
    private SwitchMaterial autoUpdateSwitch;
    private MaterialButton checkUpdateBtn;
    private MaterialButton updateNowBtn;
    private SharedPreferences appUpdatePrefs;
    private String latestDownloadUrl;
    private String latestReleaseUrl;
    private String latestNotes;
    private String latestPublishedAt;
    private String latestVersion;

    private Boolean lastKnownMaintenanceMode = null;
    private Boolean lastKnownAdminEmail2fa = null;

    private boolean binding = false;
    private Call<ApiResponse<SettingsData>> loadCall;
    private Call<ApiResponse<SettingsData>> saveCall;
    private Call<ApiResponse<AppUpdateData>> appUpdateCall;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        swipe = v.findViewById(R.id.swipe);
        accessHint = v.findViewById(R.id.accessHint);
        maintenanceSwitch = v.findViewById(R.id.maintenanceSwitch);
        admin2faSwitch = v.findViewById(R.id.admin2faSwitch);
        appLockSwitch = v.findViewById(R.id.appLockSwitch);
        saveBtn = v.findViewById(R.id.saveBtn);
        manageLabsBtn = v.findViewById(R.id.manageLabsBtn);
        manageDepartmentsBtn = v.findViewById(R.id.manageDepartmentsBtn);

        appVersionText = v.findViewById(R.id.appVersionText);
        appUpdateStatusText = v.findViewById(R.id.appUpdateStatusText);
        autoUpdateSwitch = v.findViewById(R.id.autoUpdateSwitch);
        checkUpdateBtn = v.findViewById(R.id.checkUpdateBtn);
        updateNowBtn = v.findViewById(R.id.updateNowBtn);

        appUpdatePrefs = requireContext().getSharedPreferences(APP_UPDATE_PREFS, Context.MODE_PRIVATE);
        appVersionText.setText("Current version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        boolean autoEnabled = appUpdatePrefs.getBoolean(KEY_AUTO_CHECK, true);
        autoUpdateSwitch.setChecked(autoEnabled);
        autoUpdateSwitch.setOnCheckedChangeListener((btn, isChecked) ->
                appUpdatePrefs.edit().putBoolean(KEY_AUTO_CHECK, isChecked).apply()
        );
        checkUpdateBtn.setOnClickListener(vv -> checkForAppUpdate(true));
        updateNowBtn.setOnClickListener(vv -> openAppUpdate());
        renderLastAppUpdateState();

        boolean canEdit = "super_admin".equalsIgnoreCase(store.getRole());
        accessHint.setText(canEdit ? "System settings (super_admin)" : "Access denied: super_admin only");
        setEnabled(canEdit);

        if (appLockSwitch != null) {
            appLockSwitch.setChecked(AppLockPrefs.isEnabled(requireContext()));
            appLockSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                if (!isAdded()) return;
                if (binding) return;
                if (isChecked && !AppLockSupport.canUseDeviceUnlock(requireContext())) {
                    binding = true;
                    appLockSwitch.setChecked(false);
                    binding = false;
                    Toast.makeText(requireContext(), "Enable screen lock / biometrics on your phone first", Toast.LENGTH_LONG).show();
                    return;
                }
                AppLockPrefs.setEnabled(requireContext(), isChecked);
                if (isChecked) AppLockPrefs.markUnlockedNow(requireContext());
                String msg = isChecked ? "App lock enabled" : "App lock disabled";
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                NotificationHelper.notify(requireContext(), 1203, "App security", msg);
            });
        }

        swipe.setOnRefreshListener(this::load);
        saveBtn.setOnClickListener(vv -> save());
        manageLabsBtn.setOnClickListener(vv -> startActivity(new Intent(requireContext(), LabsActivity.class)));
        manageDepartmentsBtn.setOnClickListener(vv -> startActivity(new Intent(requireContext(), DepartmentsActivity.class)));
        manageDepartmentsBtn.setVisibility(canEdit ? View.VISIBLE : View.GONE);

        load();
        return v;
    }

    private void renderLastAppUpdateState() {
        long lastCheckedAt = appUpdatePrefs.getLong(KEY_LAST_CHECKED_AT, 0L);
        String latestVer = appUpdatePrefs.getString(KEY_LAST_LATEST_VERSION, null);
        latestDownloadUrl = appUpdatePrefs.getString(KEY_LAST_DOWNLOAD_URL, null);
        latestReleaseUrl = appUpdatePrefs.getString(KEY_LAST_RELEASE_URL, null);
        latestNotes = appUpdatePrefs.getString(KEY_LAST_NOTES, null);
        latestPublishedAt = appUpdatePrefs.getString(KEY_LAST_PUBLISHED_AT, null);
        boolean updateAvailable = appUpdatePrefs.getBoolean(KEY_LAST_UPDATE_AVAILABLE, false);
        latestVersion = latestVer;

        if (lastCheckedAt <= 0) {
            appUpdateStatusText.setText("Last check: never");
            updateNowBtn.setVisibility(View.GONE);
            return;
        }

        String msg = "Last check: " + android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", lastCheckedAt);
        if (latestVer != null && !latestVer.trim().isEmpty()) {
            msg += " - Latest: " + latestVer;
        }
        appUpdateStatusText.setText(msg);

        boolean hasUrl = (latestReleaseUrl != null && !latestReleaseUrl.trim().isEmpty()) || (latestDownloadUrl != null && !latestDownloadUrl.trim().isEmpty());
        if (updateAvailable && hasUrl) {
            updateNowBtn.setVisibility(View.VISIBLE);
        } else {
            updateNowBtn.setVisibility(View.GONE);
        }
    }

    private void openAppUpdate() {
        String bestUrl = null;
        if (latestReleaseUrl != null && !latestReleaseUrl.trim().isEmpty()) bestUrl = latestReleaseUrl;
        else if (latestDownloadUrl != null && !latestDownloadUrl.trim().isEmpty()) bestUrl = latestDownloadUrl;

        if (bestUrl == null) {
            Context ctx = getContext();
            if (ctx != null) Toast.makeText(ctx, "No update link available", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(requireContext(), AppUpdateActivity.class);
        if (latestVersion != null) i.putExtra(AppUpdateActivity.EXTRA_LATEST_VERSION, latestVersion);
        if (latestDownloadUrl != null) i.putExtra(AppUpdateActivity.EXTRA_DOWNLOAD_URL, latestDownloadUrl);
        if (latestReleaseUrl != null) i.putExtra(AppUpdateActivity.EXTRA_RELEASE_URL, latestReleaseUrl);
        if (latestNotes != null) i.putExtra(AppUpdateActivity.EXTRA_NOTES, latestNotes);
        if (latestPublishedAt != null) i.putExtra(AppUpdateActivity.EXTRA_PUBLISHED_AT, latestPublishedAt);
        startActivity(i);
    }

    private void checkForAppUpdate(boolean userInitiated) {
        checkUpdateBtn.setEnabled(false);
        updateNowBtn.setEnabled(false);
        if (appUpdateCall != null) appUpdateCall.cancel();

        appUpdateCall = api.appUpdate("android", BuildConfig.VERSION_NAME);
        appUpdateCall.enqueue(new Callback<ApiResponse<AppUpdateData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AppUpdateData>> call, Response<ApiResponse<AppUpdateData>> response) {
                if (!isAdded()) return;
                checkUpdateBtn.setEnabled(true);

                if (response.code() == 404) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Missing API: /android_api/app_update.php (upload it to server)", Toast.LENGTH_LONG).show();
                    renderLastAppUpdateState();
                    return;
                }

                if (!response.isSuccessful() || response.body() == null || !response.body().status || response.body().data == null) {
                    Context ctx = getContext();
                    if (ctx != null && userInitiated) Toast.makeText(ctx, "Update check failed", Toast.LENGTH_SHORT).show();
                    renderLastAppUpdateState();
                    return;
                }

                AppUpdateData d = response.body().data;
                String latestVer = d.latest != null ? d.latest.version : null;
                String downloadUrl = d.latest != null ? d.latest.download_url : null;
                String releaseUrl = d.latest != null ? d.latest.release_url : null;
                String notes = d.latest != null ? d.latest.notes : null;
                String publishedAt = d.latest != null ? d.latest.published_at : null;

                appUpdatePrefs.edit()
                        .putLong(KEY_LAST_CHECKED_AT, System.currentTimeMillis())
                        .putString(KEY_LAST_LATEST_VERSION, latestVer)
                        .putString(KEY_LAST_DOWNLOAD_URL, downloadUrl)
                        .putString(KEY_LAST_RELEASE_URL, releaseUrl)
                        .putString(KEY_LAST_NOTES, notes)
                        .putString(KEY_LAST_PUBLISHED_AT, publishedAt)
                        .putBoolean(KEY_LAST_UPDATE_AVAILABLE, d.update_available && latestVer != null && !latestVer.trim().isEmpty())
                        .apply();

                latestDownloadUrl = downloadUrl;
                latestReleaseUrl = releaseUrl;
                latestNotes = notes;
                latestPublishedAt = publishedAt;
                latestVersion = latestVer;
                if (d.update_available && latestVer != null) {
                    appUpdateStatusText.setText("Update available: " + latestVer);
                    boolean hasUrl = (releaseUrl != null && !releaseUrl.trim().isEmpty()) || (downloadUrl != null && !downloadUrl.trim().isEmpty());
                    updateNowBtn.setVisibility(hasUrl ? View.VISIBLE : View.GONE);
                    updateNowBtn.setEnabled(true);
                    Context ctx = getContext();
                    if (ctx != null && userInitiated) Toast.makeText(ctx, "New version available: " + latestVer, Toast.LENGTH_SHORT).show();
                } else {
                    appUpdateStatusText.setText("Up to date");
                    updateNowBtn.setVisibility(View.GONE);
                    Context ctx = getContext();
                    if (ctx != null && userInitiated) Toast.makeText(ctx, "App is up to date", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AppUpdateData>> call, Throwable t) {
                if (!isAdded()) return;
                checkUpdateBtn.setEnabled(true);
                renderLastAppUpdateState();
                Context ctx = getContext();
                if (ctx != null && userInitiated) Toast.makeText(ctx, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setEnabled(boolean enabled) {
        maintenanceSwitch.setEnabled(enabled);
        admin2faSwitch.setEnabled(enabled);
        saveBtn.setEnabled(enabled);
        if (appLockSwitch != null) appLockSwitch.setEnabled(true);
    }

    private void setLoading(boolean loading) {
        swipe.setRefreshing(loading);
        if (!loading) return;
        saveBtn.setEnabled(false);
        maintenanceSwitch.setEnabled(false);
        admin2faSwitch.setEnabled(false);
        if (appLockSwitch != null) appLockSwitch.setEnabled(false);
    }

    private void load() {
        setLoading(true);
        if (loadCall != null) loadCall.cancel();
        loadCall = api.settings();
        loadCall.enqueue(new Callback<ApiResponse<SettingsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<SettingsData>> call, Response<ApiResponse<SettingsData>> response) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);

                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.code() == 403) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Access denied (super_admin only)", Toast.LENGTH_SHORT).show();
                    setEnabled(false);
                    return;
                }
                if (response.code() == 404) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Missing API: /android_api/settings.php (upload it to server)", Toast.LENGTH_LONG).show();
                    setEnabled(false);
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || !response.body().status || response.body().data == null) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Failed to load settings", Toast.LENGTH_SHORT).show();
                    setEnabled("super_admin".equalsIgnoreCase(store.getRole()));
                    return;
                }

                binding = true;
                boolean mm = response.body().data.maintenance_mode;
                boolean mfa = response.body().data.admin_email_2fa;
                maintenanceSwitch.setChecked(mm);
                admin2faSwitch.setChecked(mfa);
                binding = false;

                lastKnownMaintenanceMode = response.body().data.maintenance_mode;
                lastKnownAdminEmail2fa = response.body().data.admin_email_2fa;

                setEnabled("super_admin".equalsIgnoreCase(store.getRole()));
            }

            @Override
            public void onFailure(Call<ApiResponse<SettingsData>> call, Throwable t) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                setEnabled("super_admin".equalsIgnoreCase(store.getRole()));
            }
        });
    }

    private void save() {
        if (binding) return;

        boolean desiredMaintenance = maintenanceSwitch.isChecked();
        boolean desired2fa = admin2faSwitch.isChecked();
        if (lastKnownMaintenanceMode != null && lastKnownAdminEmail2fa != null) {
            if (desiredMaintenance == lastKnownMaintenanceMode && desired2fa == lastKnownAdminEmail2fa) {
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "No changes to save", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        final Boolean prevMaintenance = lastKnownMaintenanceMode;
        final Boolean prev2fa = lastKnownAdminEmail2fa;

        setLoading(true);
        if (saveCall != null) saveCall.cancel();
        saveCall = api.updateSettings(new SettingsUpdateRequest(
                desiredMaintenance,
                desired2fa
        ));
        saveCall.enqueue(new Callback<ApiResponse<SettingsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<SettingsData>> call, Response<ApiResponse<SettingsData>> response) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.code() == 403) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Access denied (super_admin only)", Toast.LENGTH_SHORT).show();
                    setEnabled(false);
                    return;
                }
                if (response.code() == 404) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Missing API: /android_api/settings.php (upload it to server)", Toast.LENGTH_LONG).show();
                    setEnabled(false);
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || !response.body().status) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Failed to save settings", Toast.LENGTH_SHORT).show();
                    restoreLastKnownSettings();
                    setEnabled("super_admin".equalsIgnoreCase(store.getRole()));
                    return;
                }

                SettingsData d = response.body().data;
                boolean newMaintenance = (d != null) ? d.maintenance_mode : desiredMaintenance;
                boolean new2fa = (d != null) ? d.admin_email_2fa : desired2fa;

                binding = true;
                maintenanceSwitch.setChecked(newMaintenance);
                admin2faSwitch.setChecked(new2fa);
                binding = false;

                lastKnownMaintenanceMode = newMaintenance;
                lastKnownAdminEmail2fa = new2fa;

                Context ctx = getContext();
                if (ctx != null) {
                    String msg = buildSettingsSavedMessage(prevMaintenance, prev2fa, newMaintenance, new2fa);
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
                    Intent ui = new Intent(ctx, MainActivity.class);
                    ui.putExtra(MainActivity.EXTRA_START_TAB, MainActivity.TAB_SETTINGS);
                    int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        piFlags |= PendingIntent.FLAG_IMMUTABLE;
                    }
                    PendingIntent pi = PendingIntent.getActivity(ctx, NOTIFICATION_ID_SETTINGS, ui, piFlags);
                    NotificationHelper.notify(ctx, NOTIFICATION_ID_SETTINGS, "System settings", msg, pi);
                }
                setEnabled("super_admin".equalsIgnoreCase(store.getRole()));
            }

            @Override
            public void onFailure(Call<ApiResponse<SettingsData>> call, Throwable t) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                restoreLastKnownSettings();
                setEnabled("super_admin".equalsIgnoreCase(store.getRole()));
            }
        });
    }

    private void restoreLastKnownSettings() {
        if (maintenanceSwitch == null || admin2faSwitch == null) return;
        if (lastKnownMaintenanceMode == null || lastKnownAdminEmail2fa == null) return;
        binding = true;
        maintenanceSwitch.setChecked(lastKnownMaintenanceMode);
        admin2faSwitch.setChecked(lastKnownAdminEmail2fa);
        binding = false;
    }

    private static String buildSettingsSavedMessage(@Nullable Boolean prevMaintenance,
                                                   @Nullable Boolean prev2fa,
                                                   boolean newMaintenance,
                                                   boolean new2fa) {
        List<String> parts = new ArrayList<>(2);
        if (prevMaintenance != null && prevMaintenance != newMaintenance) {
            parts.add("Maintenance mode " + (newMaintenance ? "enabled" : "disabled"));
        }
        if (prev2fa != null && prev2fa != new2fa) {
            parts.add("Email 2FA " + (new2fa ? "enabled" : "disabled"));
        }
        if (!parts.isEmpty()) {
            return TextUtils.join(" · ", parts);
        }
        return "Settings saved. Maintenance: " + (newMaintenance ? "ON" : "OFF") + ", Email 2FA: " + (new2fa ? "ON" : "OFF");
    }

    @Override
    public void onDestroyView() {
        if (loadCall != null) { loadCall.cancel(); loadCall = null; }
        if (saveCall != null) { saveCall.cancel(); saveCall = null; }
        if (appUpdateCall != null) { appUpdateCall.cancel(); appUpdateCall = null; }
        swipe = null;
        accessHint = null;
        maintenanceSwitch = null;
        admin2faSwitch = null;
        appLockSwitch = null;
        saveBtn = null;
        manageLabsBtn = null;
        manageDepartmentsBtn = null;
        appVersionText = null;
        appUpdateStatusText = null;
        autoUpdateSwitch = null;
        checkUpdateBtn = null;
        updateNowBtn = null;
        super.onDestroyView();
    }
}


