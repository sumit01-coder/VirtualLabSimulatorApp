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
import com.sumit.virtuallabadmin.v29.R;
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
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ddos_top_ip, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        DdosTopIp item = items.get(position);
        holder.rank.setText("#" + (position + 1));
        holder.ip.setText(item.ip != null ? item.ip : "");
        holder.total.setText(String.valueOf(item.total));
        holder.errors.setText(String.valueOf(item.errors));
        holder.endpoints.setText(item.endpoints + " endpoints");

        boolean selected = item.ip != null && item.ip.equals(selectedIp);
        holder.card.setStrokeWidth(dp(holder.card, selected ? 2 : 1));
        holder.card.setStrokeColor(ContextCompat.getColor(holder.card.getContext(), selected ? R.color.ddos_blue : R.color.ddos_stroke));

        int badgeRes = R.drawable.bg_ddos_badge_safe;
        int badgeTextColor = R.color.ddos_green;
        if (item.rf_class >= 2) {
            badgeRes = R.drawable.bg_ddos_badge_danger;
            badgeTextColor = R.color.ddos_red;
        } else if (item.rf_class == 1) {
            badgeRes = R.drawable.bg_ddos_badge_warn;
            badgeTextColor = R.color.ddos_yellow;
        }
        holder.rf.setText(item.rf_label != null ? item.rf_label : "Low");
        holder.rf.setBackgroundResource(badgeRes);
        holder.rf.setTextColor(ContextCompat.getColor(holder.rf.getContext(), badgeTextColor));

        boolean risky = item.rf_class >= 1;
        holder.block.setText(risky ? "Block" : "Actions");
        holder.block.setBackgroundTintList(ContextCompat.getColorStateList(holder.block.getContext(), risky ? R.color.ddos_red : R.color.ddos_indigo));
        holder.block.setOnClickListener(v -> {
            if (listener == null || item.ip == null || item.ip.trim().isEmpty()) {
                return;
            }
            if (risky) {
                listener.onQuickBlock(item);
            } else {
                listener.onSelectIp(item.ip.trim());
            }
        });
        holder.analyze.setOnClickListener(v -> {
            if (listener == null || item.ip == null || item.ip.trim().isEmpty()) {
                return;
            }
            listener.onSelectIp(item.ip.trim());
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener == null || item.ip == null || item.ip.trim().isEmpty()) {
                return;
            }
            listener.onSelectIp(item.ip.trim());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView rank;
        final TextView ip;
        final TextView total;
        final TextView errors;
        final TextView endpoints;
        final TextView rf;
        final MaterialButton analyze;
        final MaterialButton block;

        VH(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            rank = itemView.findViewById(R.id.rank);
            ip = itemView.findViewById(R.id.ip);
            total = itemView.findViewById(R.id.total);
            errors = itemView.findViewById(R.id.errors);
            endpoints = itemView.findViewById(R.id.endpoints);
            rf = itemView.findViewById(R.id.rf);
            analyze = itemView.findViewById(R.id.analyze);
            block = itemView.findViewById(R.id.block);
        }
    }

    private static int dp(View view, int value) {
        float density = view.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(value * density));
    }
}
