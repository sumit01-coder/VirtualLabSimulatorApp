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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.virtuallab.admin.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.Department;
import com.virtuallab.admin.model.DepartmentActionRequest;
import com.virtuallab.admin.ui.list.DepartmentsAdapter;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DepartmentsFragment extends BaseAuthedFragment implements DepartmentsAdapter.Listener {
    private ApiService api;
    private TokenStore store;

    private SwipeRefreshLayout swipe;
    private TextInputEditText searchInput;
    private TextView countText;
    private TextView emptyText;
    private FloatingActionButton addFab;

    private DepartmentsAdapter adapter;
    private final List<Department> all = new ArrayList<>();

    private Call<ApiResponse<List<Department>>> loadCall;
    private Call<ApiResponse<Object>> actionCall;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_departments, container, false);
        store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        swipe = v.findViewById(R.id.swipe);
        searchInput = v.findViewById(R.id.searchInput);
        countText = v.findViewById(R.id.countText);
        emptyText = v.findViewById(R.id.emptyText);
        addFab = v.findViewById(R.id.addFab);

        RecyclerView list = v.findViewById(R.id.list);
        adapter = new DepartmentsAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        boolean isSuper = "super_admin".equalsIgnoreCase(store.getRole());
        addFab.setVisibility(isSuper ? View.VISIBLE : View.GONE);
        addFab.setOnClickListener(vv -> openEditDialog(null));

        swipe.setOnRefreshListener(this::load);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (adapter != null) adapter.setQuery(s != null ? s.toString() : "");
                updateCountAndEmpty();
            }
        });

        load();
        return v;
    }

    private void load() {
        if (loadCall != null) { loadCall.cancel(); loadCall = null; }
        if (swipe != null) swipe.setRefreshing(true);

        loadCall = api.departments();
        loadCall.enqueue(new Callback<ApiResponse<List<Department>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Department>>> call, Response<ApiResponse<List<Department>>> response) {
                if (!isAdded()) return;
                if (swipe != null) swipe.setRefreshing(false);

                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.code() == 403) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Access denied (super_admin only)", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || !response.body().status || response.body().data == null) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Failed to load departments", Toast.LENGTH_SHORT).show();
                    return;
                }

                all.clear();
                all.addAll(response.body().data);
                adapter.submit(all);
                adapter.setQuery(searchInput.getText() != null ? searchInput.getText().toString() : "");
                updateCountAndEmpty();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Department>>> call, Throwable t) {
                if (!isAdded()) return;
                if (swipe != null) swipe.setRefreshing(false);
                if (call.isCanceled()) return;
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCountAndEmpty() {
        if (countText == null || emptyText == null || adapter == null) return;
        int total = adapter.getTotalCount();
        int shown = adapter.getVisibleCount();
        if (total == 0) countText.setText("");
        else if (shown == total) countText.setText(total + " departments");
        else countText.setText(shown + " / " + total);
        emptyText.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOpen(Department department) {
        openEditDialog(department);
    }

    @Override
    public void onDelete(Department department) {
        if (department == null) return;
        if (!"super_admin".equalsIgnoreCase(store.getRole())) {
            Context ctx = getContext();
            if (ctx != null) Toast.makeText(ctx, "Access denied (super_admin only)", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete department?")
                .setMessage("This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, which) -> doDelete(department))
                .show();
    }

    private void openEditDialog(@Nullable Department editing) {
        if (getContext() == null) return;
        if (!"super_admin".equalsIgnoreCase(store.getRole())) {
            // Non-super admins can view only.
            if (editing != null) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(editing.name != null ? editing.name : "Department")
                        .setMessage(editing.description != null ? editing.description : "")
                        .setPositiveButton("OK", null)
                        .show();
            }
            return;
        }

        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_department_edit, null, false);
        TextInputEditText nameInput = content.findViewById(R.id.nameInput);
        TextInputEditText descInput = content.findViewById(R.id.descInput);
        TextInputEditText iconInput = content.findViewById(R.id.iconInput);
        TextInputLayout nameLayout = content.findViewById(R.id.nameLayout);

        if (editing != null) {
            nameInput.setText(editing.name != null ? editing.name : "");
            descInput.setText(editing.description != null ? editing.description : "");
            iconInput.setText(editing.icon_class != null ? editing.icon_class : "");
        }

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(editing != null ? "Edit Department" : "Add Department")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(editing != null ? "Save" : "Add", null);

        if (editing != null) {
            b.setNeutralButton("Delete", (d, which) -> onDelete(editing));
        }

        androidx.appcompat.app.AlertDialog dialog = b.create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
            String desc = descInput.getText() != null ? descInput.getText().toString().trim() : "";
            String icon = iconInput.getText() != null ? iconInput.getText().toString().trim() : "";

            if (name.isEmpty()) {
                nameLayout.setError("Required");
                return;
            }
            nameLayout.setError(null);

            if (editing == null) doAdd(name, desc, icon, dialog);
            else doEdit(editing, name, desc, icon, dialog);
        }));
        dialog.show();
    }

    private void doAdd(String name, String desc, String icon, androidx.appcompat.app.AlertDialog dialog) {
        if (actionCall != null) actionCall.cancel();
        actionCall = api.departmentAction(DepartmentActionRequest.add(name, desc, icon));
        actionCall.enqueue(new ActionCallback(dialog, "Department added"));
    }

    private void doEdit(Department editing, String name, String desc, String icon, androidx.appcompat.app.AlertDialog dialog) {
        if (actionCall != null) actionCall.cancel();
        actionCall = api.departmentAction(DepartmentActionRequest.edit(editing.id, name, desc, icon));
        actionCall.enqueue(new ActionCallback(dialog, "Department updated"));
    }

    private void doDelete(Department d) {
        if (actionCall != null) actionCall.cancel();
        actionCall = api.departmentAction(DepartmentActionRequest.delete(d.id));
        actionCall.enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                if (!isAdded()) return;
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (!response.isSuccessful() || response.body() == null || !response.body().status) {
                    Context ctx = getContext();
                    String msg = "Failed to delete department";
                    if (response.body() != null && response.body().message != null) msg = response.body().message;
                    if (ctx != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
                    return;
                }
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Department deleted", Toast.LENGTH_SHORT).show();
                load();
            }

            @Override
            public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private final class ActionCallback implements Callback<ApiResponse<Object>> {
        private final androidx.appcompat.app.AlertDialog dialog;
        private final String successMsg;

        ActionCallback(androidx.appcompat.app.AlertDialog dialog, String successMsg) {
            this.dialog = dialog;
            this.successMsg = successMsg;
        }

        @Override
        public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
            if (!isAdded()) return;
            if (response.code() == 401) { handleUnauthorized(); return; }
            if (!response.isSuccessful() || response.body() == null || !response.body().status) {
                Context ctx = getContext();
                String msg = response.body() != null && response.body().message != null ? response.body().message : "Request failed";
                if (ctx != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
                return;
            }
            if (dialog != null) dialog.dismiss();
            Context ctx = getContext();
            if (ctx != null) Toast.makeText(ctx, successMsg, Toast.LENGTH_SHORT).show();
            load();
        }

        @Override
        public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
            if (!isAdded()) return;
            if (call.isCanceled()) return;
            Context ctx = getContext();
            if (ctx != null) Toast.makeText(ctx, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        if (loadCall != null) { loadCall.cancel(); loadCall = null; }
        if (actionCall != null) { actionCall.cancel(); actionCall = null; }
        swipe = null;
        searchInput = null;
        countText = null;
        emptyText = null;
        addFab = null;
        super.onDestroyView();
    }
}

