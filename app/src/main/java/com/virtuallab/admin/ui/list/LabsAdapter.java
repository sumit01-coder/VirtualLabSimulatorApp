package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sumit.virtuallabadmin.v28.R;
import com.virtuallab.admin.model.Lab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LabsAdapter extends RecyclerView.Adapter<LabsAdapter.VH> {
    public interface Listener {
        void onOpen(Lab lab);
        void onDelete(Lab lab);
    }

    private final Listener listener;
    private final List<Lab> all = new ArrayList<>();
    private final List<Lab> visible = new ArrayList<>();
    private String query = "";

    public LabsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Lab> next) {
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

        for (Lab l : all) {
            if (l == null) continue;
            if (needle.isEmpty()) {
                visible.add(l);
                continue;
            }
            if (contains(l.name, needle) || contains(l.subject, needle) || contains(l.topics, needle) || contains(l.department_name, needle)) {
                visible.add(l);
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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lab, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Lab l = visible.get(position);

        h.name.setText(l.name != null && !l.name.trim().isEmpty() ? l.name : "(no name)");
        h.subject.setText(l.subject != null ? l.subject : "");
        h.department.setText(l.department_name != null ? l.department_name : "");

        if (l.topics != null && !l.topics.trim().isEmpty()) {
            h.topics.setVisibility(View.VISIBLE);
            h.topics.setText(l.topics.trim());
        } else {
            h.topics.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(l);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onDelete(l);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView subject;
        final TextView topics;
        final TextView department;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            subject = itemView.findViewById(R.id.subject);
            topics = itemView.findViewById(R.id.topics);
            department = itemView.findViewById(R.id.department);
        }
    }
}

