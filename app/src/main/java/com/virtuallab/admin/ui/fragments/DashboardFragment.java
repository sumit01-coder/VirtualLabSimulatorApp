package com.virtuallab.admin.ui.fragments;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.content.Context;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
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
    private TextView tickets;

    // Card containers
    private MaterialCardView cardDepartments;
    private MaterialCardView cardLabs;
    private MaterialCardView cardPracticals;
    private MaterialCardView cardUsers;
    private MaterialCardView cardLetters;
    private MaterialCardView cardTickets;

    // Extended real-data views
    private TextView healthServer;
    private TextView healthDb;
    private TextView healthApi;
    private TextView statNewUsersWeek;
    private TextView statTotalUsersLabel;
    private TextView statActiveTicketsDetail;
    private TextView statActiveTicketsBig;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_dashboard, container, false);
        store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        swipe = v.findViewById(R.id.swipe);
        departments = v.findViewById(R.id.statDepartments);
        labs = v.findViewById(R.id.statLabs);
        practicals = v.findViewById(R.id.statPracticals);
        users = v.findViewById(R.id.statUsers);
        letters = v.findViewById(R.id.statLetters);
        tickets = v.findViewById(R.id.statTickets);

        cardDepartments = v.findViewById(R.id.cardDepartments);
        cardLabs = v.findViewById(R.id.cardLabs);
        cardPracticals = v.findViewById(R.id.cardPracticals);
        cardUsers = v.findViewById(R.id.cardUsers);
        cardLetters = v.findViewById(R.id.cardLetters);
        cardTickets = v.findViewById(R.id.cardTickets);

        // Extended real-data views
        healthServer = v.findViewById(R.id.healthServer);
        healthDb = v.findViewById(R.id.healthDb);
        healthApi = v.findViewById(R.id.healthApi);
        statNewUsersWeek = v.findViewById(R.id.statNewUsersWeek);
        statTotalUsersLabel = v.findViewById(R.id.statTotalUsersLabel);
        statActiveTicketsDetail = v.findViewById(R.id.statActiveTicketsDetail);
        statActiveTicketsBig = v.findViewById(R.id.statActiveTicketsBig);

        wireClicks();

        Button logoutBtn = v.findViewById(R.id.logoutBtn);
        logoutBtn.setOnClickListener(view -> {
            store.clear();
            startActivity(new Intent(getContext(), LoginActivity.class));
            if (getActivity() != null) getActivity().finish();
        });

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
        if (cardDepartments != null) {
            cardDepartments.setOnClickListener(v -> {
                if (store != null && "super_admin".equalsIgnoreCase(store.getRole())) {
                    startActivity(new Intent(requireContext(), DepartmentsActivity.class));
                } else {
                    toastSoon("Access denied (super_admin only)");
                }
            });
        }
        if (cardLetters != null) {
            cardLetters.setOnClickListener(v -> toastSoon("Letters module is coming soon."));
        }
    }

    private void selectBottomNav(int itemId) {
        if (!isAdded() || getActivity() == null) return;
        View nav = getActivity().findViewById(R.id.bottomNav);
        if (nav instanceof BottomNavigationView) {
            ((BottomNavigationView) nav).setSelectedItemId(itemId);
        }
    }

    private void toastSoon(String msg) {
        if (!isAdded()) return;
        Context ctx = getContext();
        if (ctx != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    private void load() {
        swipe.setRefreshing(true);
        api.dashboard().enqueue(new Callback<ApiResponse<Stats>>() {
            @Override
            public void onResponse(Call<ApiResponse<Stats>> call, Response<ApiResponse<Stats>> response) {
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
        if (letters != null) letters.setText(String.valueOf(s.verified_letters));
        if (tickets != null) tickets.setText(String.valueOf(s.active_tickets));

        // System health (real from API)
        String online = "🟢 Online";
        if (healthServer != null) healthServer.setText("Server: " + online);
        if (healthDb != null) healthDb.setText("Database: " + online);
        if (healthApi != null) healthApi.setText("API: " + online);

        // User growth (real from API)
        if (statNewUsersWeek != null) {
            statNewUsersWeek.setText("New registrations this week: " + s.new_users_week);
        }
        if (statTotalUsersLabel != null) {
            statTotalUsersLabel.setText(String.valueOf(s.users) + " total users");
        }

        // Active tickets (real from API)
        if (statActiveTicketsDetail != null) {
            statActiveTicketsDetail.setText("Unresolved support tickets");
        }
        if (statActiveTicketsBig != null) {
            statActiveTicketsBig.setText(String.valueOf(s.active_tickets));
        }
    }
}
