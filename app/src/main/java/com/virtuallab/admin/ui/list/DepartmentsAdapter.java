package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sumit.virtuallabadmin.v28.R;
import com.virtuallab.admin.model.Department;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DepartmentsAdapter extends RecyclerView.Adapter<DepartmentsAdapter.VH> {
    public interface Listener {
        void onOpen(Department department);
        void onDelete(Department department);
    }

    private final Listener listener;
    private final List<Department> all = new ArrayList<>();
    private final List<Department> visible = new ArrayList<>();
    private String query = "";

    public DepartmentsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Department> next) {
        all.clear();
        if (next != null) all.addAll(next);
        applyFilter();
    }

    public void setQuery(String q) {
        query = q != null ? q.trim() : "";
        applyFilter();
    }

    public int getTotalCount() {
        return all.size();
    }

    public int getVisibleCount() {
        return visible.size();
    }

    private void applyFilter() {
        visible.clear();
        String needle = query.toLowerCase(Locale.US);
        for (Department d : all) {
            if (d == null) continue;
            if (needle.isEmpty()) {
                visible.add(d);
                continue;
            }
            if (contains(d.name, needle) || contains(d.description, needle) || contains(d.icon_class, needle)) {
                visible.add(d);
            }
        }
        notifyDataSetChanged();
    }

    private static boolean contains(String v, String needle) {
        if (v == null) return false;
        return v.toLowerCase(Locale.US).contains(needle);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_department, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Department d = visible.get(position);
        h.name.setText(d.name != null ? d.name : "(no name)");
        if (d.description != null && !d.description.trim().isEmpty()) {
            h.description.setVisibility(View.VISIBLE);
            h.description.setText(d.description.trim());
        } else {
            h.description.setVisibility(View.GONE);
        }
        if (d.icon_class != null && !d.icon_class.trim().isEmpty()) {
            h.icon.setVisibility(View.VISIBLE);
            h.icon.setText(d.icon_class.trim());
        } else {
            h.icon.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(d);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onDelete(d);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView description;
        final TextView icon;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            description = itemView.findViewById(R.id.description);
            icon = itemView.findViewById(R.id.iconClass);
        }
    }
}

