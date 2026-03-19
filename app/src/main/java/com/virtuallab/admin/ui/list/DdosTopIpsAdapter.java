package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.virtuallab.admin.R;
import com.virtuallab.admin.model.DdosTopIp;

import java.util.ArrayList;
import java.util.List;

public final class DdosTopIpsAdapter extends RecyclerView.Adapter<DdosTopIpsAdapter.VH> {
    public interface Listener {
        void onQuickBlock(DdosTopIp ip);
    }

    private final Listener listener;
    private final List<DdosTopIp> items = new ArrayList<>();

    public DdosTopIpsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<DdosTopIp> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ddos_top_ip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DdosTopIp r = items.get(position);
        h.ip.setText(r.ip != null ? r.ip : "");
        h.total.setText(String.valueOf(r.total));
        h.errors.setText(String.valueOf(r.errors));
        h.endpoints.setText(String.valueOf(r.endpoints));

        String label = r.rf_label != null ? r.rf_label : "";
        h.rf.setText(label);
        int bg = R.drawable.bg_chip_muted;
        if (r.rf_class == 2) bg = R.drawable.bg_chip_danger;
        else if (r.rf_class == 1) bg = R.drawable.bg_chip_warn;
        else if (r.rf_class == 0) bg = R.drawable.bg_chip_success;
        h.rf.setBackgroundResource(bg);

        h.block.setVisibility(r.rf_class >= 1 ? View.VISIBLE : View.GONE);
        h.block.setOnClickListener(v -> {
            if (listener != null) listener.onQuickBlock(r);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView ip;
        final TextView total;
        final TextView errors;
        final TextView endpoints;
        final TextView rf;
        final TextView block;

        VH(@NonNull View itemView) {
            super(itemView);
            ip = itemView.findViewById(R.id.ip);
            total = itemView.findViewById(R.id.total);
            errors = itemView.findViewById(R.id.errors);
            endpoints = itemView.findViewById(R.id.endpoints);
            rf = itemView.findViewById(R.id.rf);
            block = itemView.findViewById(R.id.block);
        }
    }
}

