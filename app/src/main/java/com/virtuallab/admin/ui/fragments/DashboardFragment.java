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
import com.sumit.virtuallabadmin.v28.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
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
    private ApiService api;
    private SwipeRefreshLayout swipe;
    private TokenStore store;

    private TextView departments;
    private TextView labs;
    private TextView practicals;
    private TextView users;
    private TextView letters;
    private TextView tickets;

    private MaterialCardView cardDepartments;
    private MaterialCardView cardLabs;
    private MaterialCardView cardPracticals;
    private MaterialCardView cardUsers;
    private MaterialCardView cardLetters;
    private MaterialCardView cardTickets;

    
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
                    Toast.makeText(getContext(), "Failed to load dashboard", Toast.LENGTH_SHORT).show();
                    return;
                }
                Stats s = response.body().data;
                departments.setText(String.valueOf(s.departments));
                labs.setText(String.valueOf(s.labs));
                practicals.setText(String.valueOf(s.practicals));
                users.setText(String.valueOf(s.users));
                letters.setText(String.valueOf(s.verified_letters));
                tickets.setText(String.valueOf(s.active_tickets));
            }

            @Override
            public void onFailure(Call<ApiResponse<Stats>> call, Throwable t) {
                swipe.setRefreshing(false);
                Toast.makeText(getContext(), "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
