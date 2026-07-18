package com.virtuallab.admin.ui.fragments;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.content.Intent;
import com.virtuallab.admin.ui.MainActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.feature.OfflineCache;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.Stats;
import com.virtuallab.admin.ui.LoginActivity;
import com.virtuallab.admin.ui.LabsActivity;
import com.virtuallab.admin.ui.UsersActivity;
import com.virtuallab.admin.ui.DepartmentsActivity;
import com.virtuallab.admin.ui.ApiHealthActivity;
import com.virtuallab.admin.ui.ThemePrefs;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DashboardFragment extends BaseAuthedFragment {
    private static final String CACHE_KEY = "dashboard.cache";
    private ApiService api;
    private SwipeRefreshLayout swipe;
    private TokenStore store;

    // Stat cards
    private TextView departments;
    private TextView labs;
    private TextView practicals;
    private TextView users;
    private TextView letters;
    private TextView lettersVerified;
    private TextView tickets;
    private TextView ticketsOpen;

    // Card containers
    private MaterialCardView cardLabs;
    private MaterialCardView cardPracticals;
    private MaterialCardView cardUsers;
    private MaterialCardView cardSecurity;
    private MaterialCardView cardApiHealth;
    private MaterialCardView cardTickets;

    // Extended real-data views

    private TextView statUsersFeatured;
    private TextView statUsersTrendFeatured;
    private TextView statUsersTrend;
    private TextView statLabsTrend;
    private TextView statPracticalsTrend;
    private TextView statDepartmentsTrend;
    private Button filterTodayBtn;
    private Button filterWeekBtn;
    private Button filterMonthBtn;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_dashboard, container, false);
        store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        View header = v.findViewById(R.id.dashboardHeader);
        if (header != null) {
            final int startLeft = header.getPaddingLeft();
            final int startTop = header.getPaddingTop();
            final int startRight = header.getPaddingRight();
            final int startBottom = header.getPaddingBottom();
            final int extraTop = getResources().getDimensionPixelSize(R.dimen.space_xl);
            ViewCompat.setOnApplyWindowInsetsListener(header, (view, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(
                        startLeft,
                        startTop + bars.top + extraTop,
                        startRight,
                        startBottom
                );
                return insets;
            });
            ViewCompat.requestApplyInsets(header);
        }

        swipe = v.findViewById(R.id.swipe);
        departments = v.findViewById(R.id.statDepartments);
        labs = v.findViewById(R.id.statLabs);
        practicals = v.findViewById(R.id.statPracticals);
        users = v.findViewById(R.id.statUsers);
        letters = v.findViewById(R.id.statLetters);
        lettersVerified = v.findViewById(R.id.statLettersVerified);
        tickets = v.findViewById(R.id.statTickets);
        ticketsOpen = v.findViewById(R.id.statTicketsOpen);

        cardLabs = v.findViewById(R.id.cardLabs);
        cardPracticals = v.findViewById(R.id.cardPracticals);
        cardUsers = v.findViewById(R.id.cardUsers);
        cardSecurity = v.findViewById(R.id.cardSecurity);
        cardApiHealth = v.findViewById(R.id.cardApiHealth);
        cardTickets = v.findViewById(R.id.cardTickets);

        // Dynamic Header
        TextView welcomeText = v.findViewById(R.id.welcomeText);
        if (welcomeText != null && store.getUsername() != null) {
            String name = store.getUsername();
            // If it's an email, just take the part before @
            if (name.contains("@")) {
                name = name.split("@")[0];
            }
            // Capitalize first letter
            if (name.length() > 0) {
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
            }
            welcomeText.setText("Hi, " + name + " \uD83D\uDC4B");
        }

        ImageView menuToggle = v.findViewById(R.id.menuToggle);
        if (menuToggle != null) {
            menuToggle.setOnClickListener(view -> showProfileMenuSheet());
        }

        // Extended real-data views

        statUsersFeatured = v.findViewById(R.id.statUsersFeatured);
        statUsersTrendFeatured = v.findViewById(R.id.statUsersTrendFeatured);
        statUsersTrend = v.findViewById(R.id.statUsersTrend);
        statLabsTrend = v.findViewById(R.id.statLabsTrend);
        statPracticalsTrend = v.findViewById(R.id.statPracticalsTrend);
        statDepartmentsTrend = v.findViewById(R.id.statDepartmentsTrend);
        filterTodayBtn = v.findViewById(R.id.filterTodayBtn);
        filterWeekBtn = v.findViewById(R.id.filterWeekBtn);
        filterMonthBtn = v.findViewById(R.id.filterMonthBtn);


        wireClicks();

        Button profileBtn = v.findViewById(R.id.profileBtn);
        if (profileBtn != null) {
            profileBtn.setOnClickListener(view -> showProfileMenuSheet());
        }

        swipe.setOnRefreshListener(this::load);
        load();
        return v;
    }

    private void wireClicks() {
        if (cardPracticals != null) {
            cardPracticals.setOnClickListener(v -> selectBottomNav(R.id.nav_practicals));
        }
        if (cardTickets != null) {
            cardTickets.setOnClickListener(v -> selectBottomNav(R.id.nav_tickets));
        }
        if (cardLabs != null) {
            cardLabs.setOnClickListener(v -> startActivity(new Intent(requireContext(), LabsActivity.class)));
        }
        if (cardUsers != null) {
            cardUsers.setOnClickListener(v -> startActivity(new Intent(requireContext(), UsersActivity.class)));
        }

        if (filterTodayBtn != null) {
            filterTodayBtn.setOnClickListener(v -> updateTimeFilter("today"));
        }
        if (filterWeekBtn != null) {
            filterWeekBtn.setOnClickListener(v -> updateTimeFilter("week"));
        }
        if (filterMonthBtn != null) {
            filterMonthBtn.setOnClickListener(v -> updateTimeFilter("month"));
        }
        // Departments card (5th card, currently named cardSecurity in layout)
        if (cardSecurity != null) {
            cardSecurity.setOnClickListener(v -> {
                if (store != null && "super_admin".equalsIgnoreCase(store.getRole())) {
                    startActivity(new Intent(requireContext(), DepartmentsActivity.class));
                } else {
                    toastSoon("Access denied (super_admin only)");
                }
            });
        }
        // Letters card (6th card, currently named cardApiHealth in layout)
        if (cardApiHealth != null) {
            cardApiHealth.setOnClickListener(v -> startActivity(new Intent(getContext(), com.virtuallab.admin.ui.LettersActivity.class)));
        }
    }

    private void selectBottomNav(int itemId) {
        if (!isAdded() || getActivity() == null) return;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).selectBottomNav(itemId);
        }
    }

    private void showProfileMenuSheet() {
        if (!isAdded() || getContext() == null) return;
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_profile_menu, null, false);
        sheet.setContentView(content);

        TextView profileName = content.findViewById(R.id.profileNameText);
        TextView profileRole = content.findViewById(R.id.profileRoleText);
        TextView avatar = content.findViewById(R.id.profileAvatarText);
        LinearLayout menuProfile = content.findViewById(R.id.menuProfile);
        LinearLayout menuSettings = content.findViewById(R.id.menuSettings);
        LinearLayout menuHelp = content.findViewById(R.id.menuHelp);
        LinearLayout menuLogout = content.findViewById(R.id.menuLogout);
        SwitchMaterial darkModeSwitch = content.findViewById(R.id.darkModeSwitch);

        String username = store != null ? store.getUsername() : "Admin";
        String role = store != null ? store.getRole() : "admin";
        if (username == null || username.trim().isEmpty()) username = "Admin";
        if (role == null || role.trim().isEmpty()) role = "admin";

        profileName.setText(username);
        profileRole.setText(role);
        avatar.setText(username.substring(0, 1).toUpperCase());

        menuProfile.setOnClickListener(v -> {
            sheet.dismiss();
            showProfileDetailsPanel();
        });

        menuSettings.setOnClickListener(v -> {
            sheet.dismiss();
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).selectBottomNav(R.id.nav_settings);
            }
        });

        darkModeSwitch.setChecked(ThemePrefs.getMode(requireContext()) == ThemePrefs.MODE_DARK);
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                ThemePrefs.setMode(requireContext(), ThemePrefs.MODE_DARK);
                darkModeSwitch.post(() -> darkModeSwitch.setChecked(false));
                toastSoon("Dark mode is currently locked to light theme.");
            } else {
                ThemePrefs.setMode(requireContext(), ThemePrefs.MODE_LIGHT);
            }
        });

        menuHelp.setOnClickListener(v -> {
            sheet.dismiss();
            toastSoon("Help & Support will be available soon.");
        });

        menuLogout.setOnClickListener(v -> {
            sheet.dismiss();
            if (store != null) store.clear();
            startActivity(new Intent(getContext(), LoginActivity.class));
            if (getActivity() != null) getActivity().finish();
        });

        sheet.show();
        content.setAlpha(0f);
        content.setTranslationY(22f);
        content.animate().alpha(1f).translationY(0f).setDuration(180).start();
    }

    private void showProfileDetailsPanel() {
        if (!isAdded() || getContext() == null) return;
        BottomSheetDialog detailsSheet = new BottomSheetDialog(requireContext());
        View detailsView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_profile_details, null, false);
        detailsSheet.setContentView(detailsView);

        TextView avatar = detailsView.findViewById(R.id.detailAvatarText);
        TextView name = detailsView.findViewById(R.id.detailNameText);
        TextView role = detailsView.findViewById(R.id.detailRoleText);
        TextView usernameValue = detailsView.findViewById(R.id.detailUsernameValue);
        TextView emailValue = detailsView.findViewById(R.id.detailEmailValue);
        Button closeBtn = detailsView.findViewById(R.id.closeProfilePanelBtn);

        String username = store != null ? store.getUsername() : "Admin";
        String email = store != null ? store.getEmail() : "";
        String userRole = store != null ? store.getRole() : "admin";
        if (username == null || username.trim().isEmpty()) username = "Admin";
        if (userRole == null || userRole.trim().isEmpty()) userRole = "admin";
        if (email == null || email.trim().isEmpty()) email = "Not available";

        avatar.setText(username.substring(0, 1).toUpperCase());
        name.setText(username);
        role.setText(userRole);
        usernameValue.setText(username);
        emailValue.setText(email);

        closeBtn.setOnClickListener(v -> detailsSheet.dismiss());
        detailsSheet.show();
    }

    private void toastSoon(String msg) {
        if (!isAdded()) return;
        Context ctx = getContext();
        if (ctx != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    private void updateTimeFilter(String selected) {
        if (getContext() == null) return;
        int activeBg = getResources().getColor(R.color.brand, null);
        int activeText = getResources().getColor(android.R.color.white, null);
        int inactiveBg = getResources().getColor(android.R.color.white, null);
        int inactiveText = getResources().getColor(R.color.text_primary, null);

        if (filterTodayBtn != null) {
            boolean on = "today".equals(selected);
            filterTodayBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(on ? activeBg : inactiveBg));
            filterTodayBtn.setTextColor(on ? activeText : inactiveText);
        }
        if (filterWeekBtn != null) {
            boolean on = "week".equals(selected);
            filterWeekBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(on ? activeBg : inactiveBg));
            filterWeekBtn.setTextColor(on ? activeText : inactiveText);
        }
        if (filterMonthBtn != null) {
            boolean on = "month".equals(selected);
            filterMonthBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(on ? activeBg : inactiveBg));
            filterMonthBtn.setTextColor(on ? activeText : inactiveText);
        }
        toastSoon("Showing " + selected + " stats");
    }

    private void load() {
        swipe.setRefreshing(true);
        api.dashboard().enqueue(new Callback<ApiResponse<Stats>>() {
            @Override
            public void onResponse(Call<ApiResponse<Stats>> call, Response<ApiResponse<Stats>> response) {
                if (!isAdded() || getContext() == null) return;
                swipe.setRefreshing(false);
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || !response.body().status || response.body().data == null) {
                    Stats cached = OfflineCache.getObject(requireContext(), CACHE_KEY, Stats.class);
                    if (cached != null) {
                        bindStats(cached);
                        Toast.makeText(getContext(), "Loaded cached dashboard", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Failed to load dashboard", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                Stats s = response.body().data;
                OfflineCache.putObject(requireContext(), CACHE_KEY, s);
                bindStats(s);
            }

            @Override
            public void onFailure(Call<ApiResponse<Stats>> call, Throwable t) {
                if (!isAdded() || getContext() == null) return;
                swipe.setRefreshing(false);
                Stats cached = OfflineCache.getObject(requireContext(), CACHE_KEY, Stats.class);
                if (cached != null) {
                    bindStats(cached);
                    Toast.makeText(getContext(), "Offline mode: cached dashboard", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void bindStats(Stats s) {
        if (s == null) return;

        // Stat cards
        if (departments != null) departments.setText(String.valueOf(s.departments));
        if (labs != null) labs.setText(String.valueOf(s.labs));
        if (practicals != null) practicals.setText(String.valueOf(s.practicals));
        if (users != null) users.setText(String.valueOf(s.users));
        if (statUsersFeatured != null) statUsersFeatured.setText(String.valueOf(s.users));
        if (letters != null) letters.setText(String.valueOf(s.total_letters));
        if (lettersVerified != null) lettersVerified.setText(s.verified_letters + " Verified");
        if (tickets != null) tickets.setText(String.valueOf(s.total_tickets));
        if (ticketsOpen != null) ticketsOpen.setText(s.active_tickets + " Open");
        if (statUsersTrendFeatured != null) {
            String trend = s.new_users_month >= 0 ? "▲ +" + s.new_users_month : "▼ " + s.new_users_month;
            statUsersTrendFeatured.setText(trend + " this month");
        }
        if (statUsersTrend != null) {
            statUsersTrend.setText((s.new_users_month >= 0 ? "↑ +" : "↓ ") + s.new_users_month + " this month");
        }
        if (statLabsTrend != null) {
            statLabsTrend.setText((s.new_labs_month >= 0 ? "↑ +" : "↓ ") + s.new_labs_month + " this month");
        }
        if (statPracticalsTrend != null) {
            statPracticalsTrend.setText((s.new_practicals_month >= 0 ? "↑ +" : "↓ ") + s.new_practicals_month + " this month");
        }
        if (statDepartmentsTrend != null) {
            statDepartmentsTrend.setText((s.new_departments_month >= 0 ? "↑ +" : "↓ ") + s.new_departments_month + " this month");
        }



    }
}
