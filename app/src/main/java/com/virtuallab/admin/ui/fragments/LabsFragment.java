package com.virtuallab.admin.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.api.ApiClient;
import com.virtuallab.admin.api.ApiService;
import com.virtuallab.admin.data.TokenStore;
import com.virtuallab.admin.model.ApiResponse;
import com.virtuallab.admin.model.Department;
import com.virtuallab.admin.model.Lab;
import com.virtuallab.admin.model.LabActionRequest;
import com.virtuallab.admin.ui.list.LabsAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class LabsFragment extends BaseAuthedFragment implements LabsAdapter.Listener {
    private ApiService api;
    private TokenStore store;

    private SwipeRefreshLayout swipe;
    private TextInputEditText searchInput;
    private TextInputLayout deptFilterLayout;
    private MaterialAutoCompleteTextView deptFilterInput;
    private TextView countText;
    private TextView emptyText;
    private FloatingActionButton addFab;
    private RecyclerView list;

    private LabsAdapter adapter;
    private final List<Lab> all = new ArrayList<>();

    private final List<Department> departments = new ArrayList<>();
    private final Map<String, Integer> deptNameToId = new HashMap<>();
    private boolean isSuperAdmin = false;
    private int selectedDeptId = 0;

    private Call<ApiResponse<List<Department>>> departmentsCall;
    private Call<ApiResponse<List<Lab>>> labsCall;
    private Call<ApiResponse<Object>> actionCall;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_labs, container, false);
        store = new TokenStore(requireContext());
        api = ApiClient.get(store);

        isSuperAdmin = "super_admin".equalsIgnoreCase(store.getRole());

        swipe = v.findViewById(R.id.swipe);
        searchInput = v.findViewById(R.id.searchInput);
        deptFilterLayout = v.findViewById(R.id.deptFilterLayout);
        deptFilterInput = v.findViewById(R.id.deptFilterInput);
        countText = v.findViewById(R.id.countText);
        emptyText = v.findViewById(R.id.emptyText);
        addFab = v.findViewById(R.id.addFab);

        list = v.findViewById(R.id.list);
        adapter = new LabsAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        swipe.setOnRefreshListener(this::load);
        addFab.setOnClickListener(vv -> openEditDialog(null));

        if (!isSuperAdmin) {
            deptFilterLayout.setVisibility(View.GONE);
        }

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setQuery(s != null ? s.toString() : "");
                updateCountAndEmpty();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        load();
        return v;
    }

    private void load() {
        if (departmentsCall != null) { departmentsCall.cancel(); departmentsCall = null; }
        if (labsCall != null) { labsCall.cancel(); labsCall = null; }

        if (swipe != null) swipe.setRefreshing(true);

        if (isSuperAdmin) {
            loadDepartmentsThenLabs();
        } else {
            loadLabs();
        }
    }

    private void loadDepartmentsThenLabs() {
        departmentsCall = api.departments();
        departmentsCall.enqueue(new Callback<ApiResponse<List<Department>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Department>>> call, Response<ApiResponse<List<Department>>> response) {
                if (!isAdded()) return;
                if (response.code() == 401) { handleUnauthorized(); return; }

                if (response.code() == 404) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Missing API: /android_api/departments.php (upload it to server)", Toast.LENGTH_LONG).show();
                    loadLabs();
                    return;
                }

                if (!response.isSuccessful() || response.body() == null || !response.body().status || response.body().data == null) {
                    loadLabs();
                    return;
                }

                departments.clear();
                departments.addAll(response.body().data);
                bindDepartmentFilter();
                loadLabs();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Department>>> call, Throwable t) {
                if (!isAdded()) return;
                if (call.isCanceled()) return;
                loadLabs();
            }
        });
    }

    private void bindDepartmentFilter() {
        deptNameToId.clear();
        List<String> names = new ArrayList<>();
        names.add("All Departments");
        for (Department d : departments) {
            if (d == null) continue;
            String name = d.name != null ? d.name.trim() : "";
            if (name.isEmpty()) continue;
            names.add(name);
            deptNameToId.put(name, d.id);
        }

        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, names);
        deptFilterInput.setAdapter(a);
        if (deptFilterInput.getText() == null || deptFilterInput.getText().toString().trim().isEmpty()) {
            deptFilterInput.setText("All Departments", false);
            selectedDeptId = 0;
        }
        deptFilterInput.setOnItemClickListener((parent, view, position, id) -> {
            String selected = deptFilterInput.getText() != null ? deptFilterInput.getText().toString().trim() : "";
            if (selected.equalsIgnoreCase("All Departments") || selected.isEmpty()) selectedDeptId = 0;
            else selectedDeptId = deptNameToId.containsKey(selected) ? deptNameToId.get(selected) : 0;
            loadLabs();
        });
    }

    private void loadLabs() {
        if (labsCall != null) { labsCall.cancel(); labsCall = null; }

        Integer deptId = null;
        if (isSuperAdmin && selectedDeptId > 0) deptId = selectedDeptId;

        labsCall = api.labs(deptId, null);
        labsCall.enqueue(new Callback<ApiResponse<List<Lab>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Lab>>> call, Response<ApiResponse<List<Lab>>> response) {
                if (!isAdded()) return;
                if (swipe != null) swipe.setRefreshing(false);

                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.code() == 404) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Missing API: /android_api/labs.php (upload it to server)", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || !response.body().status || response.body().data == null) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Failed to load labs", Toast.LENGTH_SHORT).show();
                    return;
                }

                all.clear();
                all.addAll(response.body().data);
                adapter.submit(all);
                adapter.setQuery(searchInput.getText() != null ? searchInput.getText().toString() : "");
                if (list != null) list.scheduleLayoutAnimation();
                updateCountAndEmpty();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Lab>>> call, Throwable t) {
                if (!isAdded()) return;
                if (swipe != null) swipe.setRefreshing(false);
                if (call.isCanceled()) return;
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCountAndEmpty() {
        if (countText == null || emptyText == null) return;
        int total = adapter.getTotalCount();
        int shown = adapter.getVisibleCount();
        if (total == 0) countText.setText("");
        else if (shown == total) countText.setText(total + " labs");
        else countText.setText(shown + " / " + total);
        emptyText.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onOpen(Lab lab) {
        openEditDialog(lab);
    }

    @Override
    public void onDelete(Lab lab) {
        if (lab == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete lab?")
                .setMessage("This will permanently delete \"" + safe(lab.name) + "\".")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, which) -> doDelete(lab))
                .show();
    }

    private void openEditDialog(@Nullable Lab editing) {
        if (getContext() == null) return;
        if (isSuperAdmin && departments.isEmpty()) {
            Context ctx = getContext();
            if (ctx != null) Toast.makeText(ctx, "Loading departments…", Toast.LENGTH_SHORT).show();
            return;
        }
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_lab_edit, null, false);

        TextInputLayout deptLayout = content.findViewById(R.id.departmentLayout);
        MaterialAutoCompleteTextView deptInput = content.findViewById(R.id.departmentInput);
        TextInputEditText nameInput = content.findViewById(R.id.nameInput);
        TextInputEditText subjectInput = content.findViewById(R.id.subjectInput);
        TextInputEditText topicsInput = content.findViewById(R.id.topicsInput);
        TextInputEditText descInput = content.findViewById(R.id.descInput);

        if (!isSuperAdmin) {
            deptLayout.setVisibility(View.GONE);
        } else {
            List<String> deptNames = new ArrayList<>();
            for (Department d : departments) {
                if (d == null) continue;
                if (d.name != null && !d.name.trim().isEmpty()) deptNames.add(d.name.trim());
            }
            ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deptNames);
            deptInput.setAdapter(a);
        }

        if (editing != null) {
            nameInput.setText(editing.name != null ? editing.name : "");
            subjectInput.setText(editing.subject != null ? editing.subject : "");
            topicsInput.setText(editing.topics != null ? editing.topics : "");
            descInput.setText(editing.description != null ? editing.description : "");
            if (isSuperAdmin) {
                String deptName = editing.department_name != null ? editing.department_name : "";
                if (!deptName.trim().isEmpty()) deptInput.setText(deptName, false);
            }
        }

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(editing != null ? "Edit Lab" : "Add Lab")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(editing != null ? "Save" : "Add", null);

        if (editing != null) {
            b.setNeutralButton("Delete", (d, which) -> onDelete(editing));
        }

        androidx.appcompat.app.AlertDialog dialog = b.create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
            String subject = subjectInput.getText() != null ? subjectInput.getText().toString().trim() : "";
            String topics = topicsInput.getText() != null ? topicsInput.getText().toString().trim() : "";
            String desc = descInput.getText() != null ? descInput.getText().toString().trim() : "";

            if (name.isEmpty()) {
                nameInput.setError("Required");
                return;
            }

            int deptId = 0;
            if (isSuperAdmin) {
                String deptName = deptInput.getText() != null ? deptInput.getText().toString().trim() : "";
                if (deptName.isEmpty()) {
                    deptLayout.setError("Select department");
                    return;
                }
                deptLayout.setError(null);
                deptId = deptNameToId.containsKey(deptName) ? deptNameToId.get(deptName) : 0;
                if (deptId <= 0) {
                    deptLayout.setError("Invalid department");
                    return;
                }
            }

            if (editing == null) doAdd(name, subject, topics, desc, deptId, dialog);
            else doEdit(editing, name, subject, topics, desc, deptId, dialog);
        }));
        dialog.show();
    }

    private void doAdd(String name, String subject, String topics, String desc, int deptId, androidx.appcompat.app.AlertDialog dialog) {
        if (actionCall != null) actionCall.cancel();
        actionCall = api.labAction(LabActionRequest.add(name, subject, topics, desc, deptId));
        actionCall.enqueue(new ActionCallback(dialog, "Lab added"));
    }

    private void doEdit(Lab editing, String name, String subject, String topics, String desc, int deptId, androidx.appcompat.app.AlertDialog dialog) {
        if (actionCall != null) actionCall.cancel();
        actionCall = api.labAction(LabActionRequest.edit(editing.id, name, subject, topics, desc, deptId));
        actionCall.enqueue(new ActionCallback(dialog, "Lab updated"));
    }

    private void doDelete(Lab lab) {
        if (actionCall != null) actionCall.cancel();
        actionCall = api.labAction(LabActionRequest.delete(lab.id));
        actionCall.enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                if (!isAdded()) return;
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (!response.isSuccessful() || response.body() == null || !response.body().status) {
                    Context ctx = getContext();
                    if (ctx != null) Toast.makeText(ctx, "Failed to delete lab", Toast.LENGTH_SHORT).show();
                    return;
                }
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Lab deleted", Toast.LENGTH_SHORT).show();
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
            if (response.code() == 404) {
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Missing API: /android_api/labs.php (upload it to server)", Toast.LENGTH_LONG).show();
                return;
            }
            if (!response.isSuccessful() || response.body() == null || !response.body().status) {
                Context ctx = getContext();
                if (ctx != null) Toast.makeText(ctx, "Failed: " + (response.body() != null ? response.body().message : ""), Toast.LENGTH_SHORT).show();
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

    private static String safe(String s) {
        return s != null ? s : "";
    }

    @Override
    public void onDestroyView() {
        if (departmentsCall != null) { departmentsCall.cancel(); departmentsCall = null; }
        if (labsCall != null) { labsCall.cancel(); labsCall = null; }
        if (actionCall != null) { actionCall.cancel(); actionCall = null; }

        swipe = null;
        searchInput = null;
        deptFilterLayout = null;
        deptFilterInput = null;
        countText = null;
        emptyText = null;
        addFab = null;
        list = null;
        super.onDestroyView();
    }
}
