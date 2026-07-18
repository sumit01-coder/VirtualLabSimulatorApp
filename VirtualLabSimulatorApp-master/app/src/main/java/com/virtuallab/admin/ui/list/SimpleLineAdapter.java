package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sumit.virtuallabadmin.v29.R;

import java.util.ArrayList;
import java.util.List;

public final class SimpleLineAdapter extends RecyclerView.Adapter<SimpleLineAdapter.VH> {
    private final List<String> rows = new ArrayList<>();

    public void submit(List<String> next) {
        rows.clear();
        if (next != null) rows.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_simple_line, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String raw = rows.get(position);
        if (raw == null) return;
        
        if (raw.contains("|")) {
            String[] parts = raw.split("\\|", 2);
            holder.title.setText(parts[0].trim());
            holder.text.setText(parts[1].trim());
            holder.text.setVisibility(View.VISIBLE);
        } else if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            holder.title.setText(parts[0].trim());
            holder.text.setText(parts[1].trim());
            holder.text.setVisibility(View.VISIBLE);
        } else {
            holder.title.setText(raw);
            holder.text.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView text;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            text = itemView.findViewById(R.id.text);
        }
    }
}
