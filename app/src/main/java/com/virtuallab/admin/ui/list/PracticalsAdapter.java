package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.model.Practical;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PracticalsAdapter extends RecyclerView.Adapter<PracticalsAdapter.VH> {
    public interface OnPracticalClickListener {
        void onPracticalClick(Practical practical);
    }

    private final List<Practical> all = new ArrayList<>();
    private final List<Practical> visible = new ArrayList<>();
    private final OnPracticalClickListener listener;
    private String query = "";
    private String dept = "All Departments";

    public PracticalsAdapter(OnPracticalClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<Practical> next) {
        all.clear();
        if (next != null) all.addAll(next);
        applyFilter();
    }

    public void setQuery(String q) {
        query = q != null ? q.trim() : "";
        applyFilter();
    }

    public void setDepartment(String d) {
        dept = d != null ? d.trim() : "All Departments";
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
        boolean allDepts = dept.isEmpty() || "All Departments".equalsIgnoreCase(dept);

        for (Practical p : all) {
            if (p == null) continue;
            if (!allDepts) {
                String pDept = p.dept_name != null ? p.dept_name.trim() : "";
                if (!pDept.equalsIgnoreCase(dept)) continue;
            }
            if (needle.isEmpty()) {
                visible.add(p);
                continue;
            }
            if (contains(p.title, needle) || contains(p.lab_name, needle) || contains(p.dept_name, needle)) {
                visible.add(p);
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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_practical, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Practical p = visible.get(position);
        String title = p.title != null ? p.title.trim() : "";
        String lab = p.lab_name != null ? p.lab_name.trim() : "";
        String dept = p.dept_name != null ? p.dept_name.trim() : "";

        h.title.setText(title.isEmpty() ? "Untitled Practical" : title);
        h.lab.setText(lab.isEmpty() ? "Lab details will be available in preview" : lab);
        h.dept.setText(dept.isEmpty() ? "General" : dept);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPracticalClick(p);
        });
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView lab;
        final TextView dept;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            lab = itemView.findViewById(R.id.lab);
            dept = itemView.findViewById(R.id.dept);
        }
    }
}

