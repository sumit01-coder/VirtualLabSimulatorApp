package com.virtuallab.admin.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.HapticFeedbackConstants;

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
import com.sumit.virtuallabadmin.v29.R;
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

    private View bottomNav;
    private View bottomNavShell;
    private FrameLayout fragmentContainer;
    private static final String UPDATES_WORK_NAME = "vl_admin_updates";
    private static final int REQ_NOTIF = 501;

    private String pendingDdosIp = null;
    private String initialTab = TAB_DASHBOARD;
    private int selectedNavId = R.id.nav_dashboard;

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
            if (subtitle != null && subtitle.contains("@")) {
                subtitle = subtitle.split("@")[0];
            }
            String role = store.getRole();
            if (role != null && !role.trim().isEmpty()) subtitle = subtitle + " \u2022 " + role.trim();
            getSupportActionBar().setSubtitle(subtitle);
        }

        bottomNav = findViewById(R.id.bottomNav);
        bottomNavShell = findViewById(R.id.bottomNavShell);
        fragmentContainer = findViewById(R.id.fragmentContainer);

        readIntentForNavigation(getIntent());

        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_settings) {
                selectBottomNav(R.id.nav_settings);
                return true;
            }
            return false;
        });

        bindBottomNavClicks();

        if (savedInstanceState == null) {
            selectBottomNav(tabToNavId(initialTab));
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
        if (navId == selectedNavId) {
            // Selected tab already visible; force refresh with deep-link args.
            navigateTo(navId, true);
        } else {
            selectBottomNav(navId);
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

    public void selectBottomNav(int id) {
        if (bottomNav == null) return;
        navigateTo(id, false);
    }

    private void showFragment(Fragment f, boolean showBottomNav) {
        if (bottomNavShell != null && bottomNavShell.getVisibility() != (showBottomNav ? View.VISIBLE : View.GONE)) {
            bottomNavShell.animate().cancel();
            if (showBottomNav) {
                bottomNavShell.setAlpha(0f);
                bottomNavShell.setVisibility(View.VISIBLE);
                bottomNavShell.animate().alpha(1f).setDuration(160).start();
            } else {
                bottomNavShell.animate().alpha(0f).setDuration(140).withEndAction(() -> bottomNavShell.setVisibility(View.GONE)).start();
            }
        }

        com.google.android.material.appbar.AppBarLayout appBar = findViewById(R.id.appBar);
        if (appBar != null) {
            boolean showAppBar = !(f instanceof DashboardFragment);
            appBar.setVisibility(showAppBar ? View.VISIBLE : View.GONE);
            syncFragmentTopMargin(appBar, showAppBar);
        }

        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(R.anim.vl_fade_slide_in, R.anim.vl_fade_out)
                .replace(R.id.fragmentContainer, f)
                .commit();
    }

    private void syncFragmentTopMargin(View appBar, boolean showAppBar) {
        if (fragmentContainer == null) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fragmentContainer.getLayoutParams();
        int targetTopMargin = 0;
        if (showAppBar) {
            targetTopMargin = appBar.getHeight();
            if (targetTopMargin == 0) {
                appBar.post(() -> syncFragmentTopMargin(appBar, true));
                return;
            }
        }
        if (lp.topMargin != targetTopMargin) {
            lp.topMargin = targetTopMargin;
            fragmentContainer.setLayoutParams(lp);
        }
    }

    private void setupEdgeToEdge() {
        View root = findViewById(R.id.root);
        View appBar = findViewById(R.id.appBar);
        View navShell = findViewById(R.id.bottomNavShell);
        View nav = findViewById(R.id.bottomNav);
        FrameLayout container = findViewById(R.id.fragmentContainer);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        final int appBarPadLeft = appBar.getPaddingLeft();
        final int appBarPadTop = appBar.getPaddingTop();
        final int appBarPadRight = appBar.getPaddingRight();
        final int appBarPadBottom = appBar.getPaddingBottom();

        final int navShellPadLeft = navShell.getPaddingLeft();
        final int navShellPadTop = navShell.getPaddingTop();
        final int navShellPadRight = navShell.getPaddingRight();
        final int navShellPadBottom = navShell.getPaddingBottom();
        final ViewGroup.MarginLayoutParams navShellLp = (ViewGroup.MarginLayoutParams) navShell.getLayoutParams();
        final int navShellBottomMargin = navShellLp.bottomMargin;

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

            navShell.setPadding(
                    navShellPadLeft + bars.left,
                    navShellPadTop,
                    navShellPadRight + bars.right,
                    navShellPadBottom
            );

            ViewGroup.MarginLayoutParams shellParams = (ViewGroup.MarginLayoutParams) navShell.getLayoutParams();
            shellParams.bottomMargin = navShellBottomMargin + bars.bottom;
            navShell.setLayoutParams(shellParams);

            nav.setPadding(
                    navPadLeft,
                    navPadTop,
                    navPadRight,
                    navPadBottom
            );

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) container.getLayoutParams();
            lp.bottomMargin = startBottomMargin + bars.bottom;
            container.setLayoutParams(lp);

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    private void bindBottomNavClicks() {
        findViewById(R.id.navDashboard).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            selectBottomNav(R.id.nav_dashboard);
        });
        findViewById(R.id.navTickets).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            selectBottomNav(R.id.nav_tickets);
        });
        findViewById(R.id.navPracticals).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            selectBottomNav(R.id.nav_practicals);
        });
        findViewById(R.id.navSettings).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            selectBottomNav(R.id.nav_settings);
        });
        findViewById(R.id.navSecurity).setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            selectBottomNav(R.id.nav_ddos);
        });
    }

    private void navigateTo(int id, boolean forceRefresh) {
        selectedNavId = id;
        updateCustomNavState(id);

        Fragment f;
        if (id == R.id.nav_settings) {
            f = new SettingsFragment();
        } else if (id == R.id.nav_dashboard) {
            f = new DashboardFragment();
        } else if (id == R.id.nav_tickets) {
            f = new TicketsFragment();
        } else if (id == R.id.nav_practicals) {
            f = new PracticalsFragment();
        } else {
            f = DdosFragment.newInstance(pendingDdosIp);
        }

        showFragment(f, true);
        if (forceRefresh || id == R.id.nav_ddos) {
            pendingDdosIp = null;
        } else {
            pendingDdosIp = null;
        }
    }

    private void updateCustomNavState(int selectedId) {
        updateNavItem(R.id.navDashboard,  R.id.navDashboardPill,  R.id.navDashboardIcon,  R.id.navDashboardLabel,  selectedId == R.id.nav_dashboard);
        updateNavItem(R.id.navTickets,    R.id.navTicketsPill,    R.id.navTicketsIcon,    R.id.navTicketsLabel,    selectedId == R.id.nav_tickets);
        updateNavItem(R.id.navPracticals, R.id.navPracticalsPill, R.id.navPracticalsIcon, R.id.navPracticalsLabel, selectedId == R.id.nav_practicals);
        updateNavItem(R.id.navSettings,   R.id.navSettingsPill,   R.id.navSettingsIcon,   R.id.navSettingsLabel,   selectedId == R.id.nav_settings);
        updateNavItem(R.id.navSecurity,   R.id.navSecurityPill,   R.id.navSecurityIcon,   R.id.navSecurityLabel,   selectedId == R.id.nav_ddos);
    }

    private void updateNavItem(int containerId, int pillId, int iconId, int labelId, boolean selected) {
        View pill   = findViewById(pillId);
        ImageView icon  = findViewById(iconId);
        TextView label  = findViewById(labelId);
        if (pill == null || icon == null || label == null) return;

        // Pill background: gradient when active, transparent when inactive
        pill.setBackgroundResource(selected ? R.drawable.bg_nav_pill_active : android.R.color.transparent);

        // Icon tint: white when active, grey when inactive
        int tint = ContextCompat.getColor(this, selected ? android.R.color.white : R.color.text_soft);
        icon.setImageTintList(ColorStateList.valueOf(tint));
        icon.setScaleX(1f);
        icon.setScaleY(1f);

        // Label: visible only when active
        label.setAlpha(1f);
        label.setMaxWidth(Integer.MAX_VALUE);
        label.setTranslationX(0f);
        label.setVisibility(selected ? View.VISIBLE : View.GONE);
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
