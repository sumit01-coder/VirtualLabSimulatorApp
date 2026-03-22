package com.virtuallab.admin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.sumit.virtuallabadmin.v28.R;
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
    public static final String EXTRA_START_TAB = "vl_extra_start_tab";
    public static final String EXTRA_DDOS_IP = "vl_extra_ddos_ip";
    public static final String TAB_DASHBOARD = "dashboard";
    public static final String TAB_TICKETS = "tickets";
    public static final String TAB_PRACTICALS = "practicals";
    public static final String TAB_DDOS = "ddos";
    public static final String TAB_SETTINGS = "settings";

    private BottomNavigationView bottomNav;
    private static final String UPDATES_WORK_NAME = "vl_admin_updates";
    private static final int REQ_NOTIF = 501;

    private String pendingDdosIp = null;
    private String initialTab = TAB_DASHBOARD;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupEdgeToEdge();

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

        readIntentForNavigation(getIntent());

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
            else f = DdosFragment.newInstance(pendingDdosIp);

            showFragment(f, true);
            // Consume the pending deep-link IP (so normal navigation doesn't keep forcing it).
            pendingDdosIp = null;
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(tabToNavId(initialTab));
            // Ensure we don't keep applying initial tab after first render.
            initialTab = TAB_DASHBOARD;
        }

        ensureNotificationPermission();
        scheduleUpdatesWorker();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntentForNavigation(intent);

        if (bottomNav == null) return;
        int navId = tabToNavId(initialTab);
        if (navId == bottomNav.getSelectedItemId()) {
            // Selected tab already visible; force refresh with deep-link args.
            if (navId == R.id.nav_settings) showFragment(new SettingsFragment(), true);
            else if (navId == R.id.nav_ddos) showFragment(DdosFragment.newInstance(pendingDdosIp), true);
        } else {
            bottomNav.setSelectedItemId(navId);
        }
        initialTab = TAB_DASHBOARD;
    }

    private void readIntentForNavigation(Intent intent) {
        if (intent == null) return;
        String tab = intent.getStringExtra(EXTRA_START_TAB);
        if (tab != null && !tab.trim().isEmpty()) {
            initialTab = tab.trim();
        }
        if (TAB_DDOS.equalsIgnoreCase(initialTab)) {
            String ip = intent.getStringExtra(EXTRA_DDOS_IP);
            if (ip != null && !ip.trim().isEmpty()) pendingDdosIp = ip.trim();
        }
    }

    private int tabToNavId(String tab) {
        if (tab == null) return R.id.nav_dashboard;
        String t = tab.trim().toLowerCase();
        if (TAB_SETTINGS.equals(t)) return R.id.nav_settings;
        if (TAB_TICKETS.equals(t)) return R.id.nav_tickets;
        if (TAB_PRACTICALS.equals(t)) return R.id.nav_practicals;
        if (TAB_DDOS.equals(t)) return R.id.nav_ddos;
        return R.id.nav_dashboard;
    }

    private void showFragment(Fragment f, boolean showBottomNav) {
        if (bottomNav.getVisibility() != (showBottomNav ? View.VISIBLE : View.GONE)) {
            bottomNav.animate().cancel();
            if (showBottomNav) {
                bottomNav.setAlpha(0f);
                bottomNav.setVisibility(View.VISIBLE);
                bottomNav.animate().alpha(1f).setDuration(160).start();
            } else {
                bottomNav.animate().alpha(0f).setDuration(140).withEndAction(() -> bottomNav.setVisibility(View.GONE)).start();
            }
        }

        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(R.anim.vl_fade_slide_in, R.anim.vl_fade_out)
                .replace(R.id.fragmentContainer, f)
                .commit();
    }

    private void setupEdgeToEdge() {
        View root = findViewById(R.id.root);
        View appBar = findViewById(R.id.appBar);
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        FrameLayout container = findViewById(R.id.fragmentContainer);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        final int appBarPadLeft = appBar.getPaddingLeft();
        final int appBarPadTop = appBar.getPaddingTop();
        final int appBarPadRight = appBar.getPaddingRight();
        final int appBarPadBottom = appBar.getPaddingBottom();

        final int navPadLeft = nav.getPaddingLeft();
        final int navPadTop = nav.getPaddingTop();
        final int navPadRight = nav.getPaddingRight();
        final int navPadBottom = nav.getPaddingBottom();

        final ViewGroup.MarginLayoutParams startLp = (ViewGroup.MarginLayoutParams) container.getLayoutParams();
        final int startBottomMargin = startLp.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            appBar.setPadding(
                    appBarPadLeft + bars.left,
                    appBarPadTop + bars.top,
                    appBarPadRight + bars.right,
                    appBarPadBottom
            );

            nav.setPadding(
                    navPadLeft + bars.left,
                    navPadTop,
                    navPadRight + bars.right,
                    navPadBottom + bars.bottom
            );

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) container.getLayoutParams();
            lp.bottomMargin = startBottomMargin + bars.bottom;
            container.setLayoutParams(lp);

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
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
