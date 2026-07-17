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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.sumit.virtuallabadmin.v29.R;
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
    private TextView totalUsersText;
    private TextView activeUsersText;
    private TextView blockedUsersText;
    private View filterBtn;
    private View addUserFab;
    private TextView chipAll;
    private TextView chipStudents;
    private TextView chipFaculty;
    private TextView chipActive;
    private TextView chipInactive;
    private Call<ApiResponse<List<User>>> pendingCall;
    private String currentFilter = "all";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_users, container, false);
        TokenStore store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        swipe = v.findViewById(R.id.swipe);
        list = v.findViewById(R.id.list);
        countText = v.findViewById(R.id.countText);
        emptyText = v.findViewById(R.id.emptyText);
        searchInput = v.findViewById(R.id.searchInput);
        totalUsersText = v.findViewById(R.id.totalUsersText);
        activeUsersText = v.findViewById(R.id.activeUsersText);
        blockedUsersText = v.findViewById(R.id.blockedUsersText);
        filterBtn = v.findViewById(R.id.filterBtn);
        addUserFab = v.findViewById(R.id.addUserFab);
        chipAll = v.findViewById(R.id.chipAll);
        chipStudents = v.findViewById(R.id.chipStudents);
        chipFaculty = v.findViewById(R.id.chipFaculty);
        chipActive = v.findViewById(R.id.chipActive);
        chipInactive = v.findViewById(R.id.chipInactive);

        adapter = new UsersAdapter(this::showUserDialog);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);
        bindChipClicks();

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (adapter != null) adapter.setQuery(s != null ? s.toString() : "");
                updateCountAndEmpty();
            }
        });

        if (filterBtn != null) {
            filterBtn.setOnClickListener(view -> Toast.makeText(requireContext(), "Filter controls coming soon", Toast.LENGTH_SHORT).show());
        }
        if (addUserFab != null) {
            addUserFab.setOnClickListener(view -> Toast.makeText(requireContext(), "Add user flow coming soon", Toast.LENGTH_SHORT).show());
        }
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
        int total = adapter != null ? adapter.getTotalCount() : 0;
        int shown = adapter != null ? adapter.getVisibleCount() : 0;
        int blocked = adapter != null ? adapter.getBlockedCount() : 0;
        int active = adapter != null ? adapter.getActiveCount() : 0;

        if (totalUsersText != null) totalUsersText.setText(String.valueOf(total));
        if (activeUsersText != null) activeUsersText.setText(String.valueOf(active));
        if (blockedUsersText != null) blockedUsersText.setText(String.valueOf(blocked));

        if (countText != null) {
            if (total == 0) {
                countText.setText("");
            } else if (shown == total) {
                countText.setText("Total: " + total + " \u2022 Active: " + active + " \u2022 Blocked: " + blocked);
            } else {
                countText.setText("Showing " + shown + " of " + total + " users");
            }
        }
        if (emptyText != null) {
            boolean show = adapter != null && adapter.getVisibleCount() == 0;
            emptyText.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showUserDialog(User u) {
        if (getContext() == null || u == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = getLayoutInflater().inflate(R.layout.bottom_sheet_user_profile, null, false);

        TextView avatar = content.findViewById(R.id.sheetAvatar);
        TextView name = content.findViewById(R.id.sheetName);
        TextView email = content.findViewById(R.id.sheetEmail);
        TextView status = content.findViewById(R.id.sheetStatus);
        TextView userInfo = content.findViewById(R.id.sheetUserInfo);
        TextView contact = content.findViewById(R.id.sheetContact);
        TextView account = content.findViewById(R.id.sheetAccount);
        TextView activity = content.findViewById(R.id.sheetActivity);
        MaterialButton editBtn = content.findViewById(R.id.sheetEditBtn);
        MaterialButton closeBtn = content.findViewById(R.id.sheetCloseBtn);

        String displayName = safe(u.full_name, "User");
        String displayEmail = safe(u.email, "No email");
        String displayStatus = safe(u.status, "active");
        String initial = displayName.substring(0, 1).toUpperCase();

        avatar.setText(initial);
        name.setText(displayName);
        email.setText(displayEmail);
        boolean blocked = "blocked".equalsIgnoreCase(displayStatus) || "inactive".equalsIgnoreCase(displayStatus);
        status.setText(blocked ? "Inactive" : "Active");
        status.setBackgroundResource(blocked ? R.drawable.bg_chip_red_soft : R.drawable.bg_chip_green_soft);
        status.setTextColor(ContextCompat.getColor(requireContext(), blocked ? R.color.danger : R.color.success));

        userInfo.setText("ID: " + u.id + "\nUsername: " + safe(u.username, "-"));
        contact.setText("Email: " + displayEmail);
        account.setText(
                "Role: " + safe(u.role, "-")
                        + "\nStatus: " + displayStatus
                        + "\nTokens: " + u.tokens
        );
        activity.setText("Created: " + safe(u.created_at, "-"));

        editBtn.setOnClickListener(view -> Toast.makeText(requireContext(), "Edit user flow coming soon", Toast.LENGTH_SHORT).show());
        closeBtn.setOnClickListener(view -> dialog.dismiss());

        dialog.setContentView(content);
        dialog.show();
    }

    private void bindChipClicks() {
        bindChip(chipAll, "all");
        bindChip(chipStudents, "students");
        bindChip(chipFaculty, "faculty");
        bindChip(chipActive, "active");
        bindChip(chipInactive, "inactive");
        updateChipState();
    }

    private void bindChip(@Nullable TextView chip, @NonNull String filter) {
        if (chip == null) return;
        chip.setOnClickListener(view -> {
            currentFilter = filter;
            if (adapter != null) adapter.setFilter(filter);
            updateChipState();
            updateCountAndEmpty();
        });
    }

    private void updateChipState() {
        updateChipView(chipAll, "all".equals(currentFilter));
        updateChipView(chipStudents, "students".equals(currentFilter));
        updateChipView(chipFaculty, "faculty".equals(currentFilter));
        updateChipView(chipActive, "active".equals(currentFilter));
        updateChipView(chipInactive, "inactive".equals(currentFilter));
    }

    private void updateChipView(@Nullable TextView chip, boolean selected) {
        if (chip == null) return;
        chip.setBackgroundResource(selected ? R.drawable.bg_users_chip_active : R.drawable.bg_users_chip);
        chip.setTextColor(ContextCompat.getColor(requireContext(), selected ? android.R.color.white : R.color.text_muted));
    }

    private static String safe(@Nullable String value, @NonNull String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
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
        totalUsersText = null;
        activeUsersText = null;
        blockedUsersText = null;
        filterBtn = null;
        addUserFab = null;
        chipAll = null;
        chipStudents = null;
        chipFaculty = null;
        chipActive = null;
        chipInactive = null;
        super.onDestroyView();
    }
}
