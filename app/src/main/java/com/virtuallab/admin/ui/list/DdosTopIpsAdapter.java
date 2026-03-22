package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.sumit.virtuallabadmin.v28.R;
import com.virtuallab.admin.model.DdosTopIp;

import java.util.ArrayList;
import java.util.List;

public final class DdosTopIpsAdapter extends RecyclerView.Adapter<DdosTopIpsAdapter.VH> {
    public interface Listener {
        void onQuickBlock(DdosTopIp ip);
        void onSelectIp(String ip);
    }

    private final Listener listener;
    private final List<DdosTopIp> items = new ArrayList<>();
    private String selectedIp = "";

    public DdosTopIpsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setSelectedIp(String ip) {
        selectedIp = ip != null ? ip : "";
        notifyDataSetChanged();
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

        boolean selected = r.ip != null && r.ip.equals(selectedIp);
        h.card.setStrokeWidth(dp(h.card, selected ? 2 : 1));
        h.card.setStrokeColor(ContextCompat.getColor(h.card.getContext(), selected ? R.color.brand : R.color.stroke));

        String label = r.rf_label != null ? r.rf_label : "";
        h.rf.setText(label);
        int bg = R.drawable.bg_chip_muted;
        if (r.rf_class == 2) bg = R.drawable.bg_chip_danger;
        else if (r.rf_class == 1) bg = R.drawable.bg_chip_warn;
        else if (r.rf_class == 0) bg = R.drawable.bg_chip_success;
        h.rf.setBackgroundResource(bg);

        h.block.setVisibility(View.VISIBLE);
        boolean risky = r.rf_class >= 1;
        h.block.setText(risky ? "Block 1h" : "Actions");
        h.block.setIconResource(risky ? R.drawable.ic_security_24 : R.drawable.ic_security_24);
        int tint = risky ? R.color.danger : R.color.brand;
        h.block.setBackgroundTintList(ContextCompat.getColorStateList(h.block.getContext(), tint));
        h.block.setOnClickListener(v -> {
            if (listener == null) return;
            if (r.ip == null || r.ip.trim().isEmpty()) return;
            if (risky) listener.onQuickBlock(r);
            else listener.onSelectIp(r.ip.trim());
        });

        h.itemView.setOnClickListener(v -> {
            if (listener == null) return;
            if (r.ip == null || r.ip.trim().isEmpty()) return;
            listener.onSelectIp(r.ip.trim());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView ip;
        final TextView total;
        final TextView errors;
        final TextView endpoints;
        final TextView rf;
        final MaterialButton block;

        VH(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            ip = itemView.findViewById(R.id.ip);
            total = itemView.findViewById(R.id.total);
            errors = itemView.findViewById(R.id.errors);
            endpoints = itemView.findViewById(R.id.endpoints);
            rf = itemView.findViewById(R.id.rf);
            block = itemView.findViewById(R.id.block);
        }
    }

    private static int dp(View v, int dp) {
        float density = v.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(dp * density));
    }
}

