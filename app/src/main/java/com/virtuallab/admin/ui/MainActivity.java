package com.virtuallab.admin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.virtuallab.admin.R;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.ui.fragments.DashboardFragment;
import com.virtuallab.admin.ui.fragments.DdosFragment;
import com.virtuallab.admin.ui.fragments.PracticalsFragment;
import com.virtuallab.admin.ui.fragments.SettingsFragment;
import com.virtuallab.admin.ui.fragments.TicketsFragment;
import com.virtuallab.admin.work.UpdatesWorker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.concurrent.TimeUnit;

public final class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNav;
    private static final String UPDATES_WORK_NAME = "vl_admin_updates";
    private static final int REQ_NOTIF = 501;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TokenStore store = new TokenStore(this);
        if (!store.hasToken()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            String subtitle = store.getUsername();
            String role = store.getRole();
            if (role != null && !role.trim().isEmpty()) subtitle = subtitle + " \u2022 " + role.trim();
            getSupportActionBar().setSubtitle(subtitle);
        }

        bottomNav = findViewById(R.id.bottomNav);

        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_settings) {
                bottomNav.setSelectedItemId(R.id.nav_settings);
                return true;
            }
            return false;
        });

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_settings) {
                showFragment(new SettingsFragment(), true);
                return true;
            }

            Fragment f;
            if (id == R.id.nav_dashboard) f = new DashboardFragment();
            else if (id == R.id.nav_tickets) f = new TicketsFragment();
            else if (id == R.id.nav_practicals) f = new PracticalsFragment();
            else f = new DdosFragment();

            showFragment(f, true);
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_dashboard);
        }

        ensureNotificationPermission();
        scheduleUpdatesWorker();
    }

    private void showFragment(Fragment f, boolean showBottomNav) {
        bottomNav.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, f)
                .commit();
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
    }

    private void scheduleUpdatesWorker() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(UpdatesWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                UPDATES_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
        );
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
