package com.virtuallab.admin.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.sumit.virtuallabadmin.v28.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.User;
import com.virtuallab.admin.ui.list.UsersAdapter;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class UsersFragment extends BaseAuthedFragment {
    private ApiService api;
    private SwipeRefreshLayout swipe;
    private UsersAdapter adapter;
    private RecyclerView list;
    private TextView countText;
    private TextView emptyText;
    private TextInputEditText searchInput;
    private Call<ApiResponse<List<User>>> pendingCall;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_users, container, false);
        TokenStore store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        swipe = v.findViewById(R.id.swipe);
        list = v.findViewById(R.id.list);
        countText = v.findViewById(R.id.countText);
        emptyText = v.findViewById(R.id.emptyText);
        searchInput = v.findViewById(R.id.searchInput);

        adapter = new UsersAdapter(this::showUserDialog);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (adapter != null) adapter.setQuery(s != null ? s.toString() : "");
                updateCountAndEmpty();
            }
        });

        swipe.setOnRefreshListener(this::load);
        load();
        return v;
    }

    private void load() {
        if (pendingCall != null) {
            pendingCall.cancel();
            pendingCall = null;
        }
        if (swipe != null) swipe.setRefreshing(true);

        pendingCall = api.users();
        pendingCall.enqueue(new Callback<ApiResponse<List<User>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<User>>> call, Response<ApiResponse<List<User>>> response) {
                if (!isAdded()) return;
                if (swipe != null) swipe.setRefreshing(false);
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.code() == 404) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Missing API: /android_api/users.php (upload it to server)", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || !response.body().status || response.body().data == null) {
                    Context ctx = getContext();
                    String msg = "Failed to load users";
                    if (response.body() != null && response.body().message != null && !response.body().message.trim().isEmpty()) {
                        msg = msg + ": " + response.body().message.trim();
                    } else if (!response.isSuccessful()) {
                        msg = msg + " (HTTP " + response.code() + ")";
                    }
                    if (ctx != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (adapter != null) adapter.submit(response.body().data);
                if (list != null) list.scheduleLayoutAnimation();
                updateCountAndEmpty();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<User>>> call, Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                if (swipe != null) swipe.setRefreshing(false);
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCountAndEmpty() {
        if (countText != null) {
            int total = adapter != null ? adapter.getTotalCount() : 0;
            int shown = adapter != null ? adapter.getVisibleCount() : 0;
            if (total == 0) {
                countText.setText("");
            } else if (shown == total) {
                int blocked = adapter != null ? adapter.getBlockedCount() : 0;
                int active = adapter != null ? adapter.getActiveCount() : 0;
                countText.setText("Total: " + total + " \u2022 Active: " + active + " \u2022 Blocked: " + blocked);
            } else {
                countText.setText(shown + " / " + total);
            }
        }
        if (emptyText != null) {
            boolean show = adapter != null && adapter.getVisibleCount() == 0;
            emptyText.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showUserDialog(User u) {
        if (getContext() == null || u == null) return;
        String title = u.full_name != null && !u.full_name.trim().isEmpty() ? u.full_name : "User";

        StringBuilder msg = new StringBuilder();
        msg.append("ID: ").append(u.id).append('\n');
        if (u.unique_id != null && !u.unique_id.isEmpty()) msg.append("Unique ID: ").append(u.unique_id).append('\n');
        if (u.username != null && !u.username.isEmpty()) msg.append("Username: ").append(u.username).append('\n');
        if (u.role != null && !u.role.isEmpty()) msg.append("Role: ").append(u.role).append('\n');
        if (u.institution != null && !u.institution.isEmpty()) msg.append("Institution: ").append(u.institution).append('\n');
        if (u.tokens > 0) msg.append("Tokens: ").append(u.tokens).append('\n');
        if (u.status != null && !u.status.isEmpty()) msg.append("Status: ").append(u.status).append('\n');
        if (u.email != null && !u.email.isEmpty()) msg.append("Email: ").append(u.email).append('\n');
        if (u.department != null && !u.department.isEmpty()) msg.append("Department: ").append(u.department).append('\n');
        if (u.current_year != null && !u.current_year.isEmpty()) msg.append("Year: ").append(u.current_year).append('\n');
        if (u.created_at != null && !u.created_at.isEmpty()) msg.append("Created: ").append(u.created_at).append('\n');

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(msg.toString().trim())
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        if (pendingCall != null) {
            pendingCall.cancel();
            pendingCall = null;
        }
        swipe = null;
        list = null;
        countText = null;
        emptyText = null;
        searchInput = null;
        super.onDestroyView();
    }
}
