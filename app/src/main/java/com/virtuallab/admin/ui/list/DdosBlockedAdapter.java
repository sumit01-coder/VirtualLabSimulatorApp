package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.sumit.virtuallabadmin.v28.R;
import com.virtuallab.admin.model.DdosBlockedIp;

import java.util.ArrayList;
import java.util.List;

public final class DdosBlockedAdapter extends RecyclerView.Adapter<DdosBlockedAdapter.VH> {
    public interface Listener {
        void onUnblock(DdosBlockedIp ip);
        void onSelectBlockedIp(String ip);
    }

    private final Listener listener;
    private final List<DdosBlockedIp> items = new ArrayList<>();
    private String selectedIp = "";

    public DdosBlockedAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<DdosBlockedIp> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    public void setSelectedIp(String ip) {
        selectedIp = ip != null ? ip : "";
        notifyDataSetChanged();
    }

    
    @Override
    public VH onCreateViewHolder( ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blocked_ip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder( VH h, int position) {
        DdosBlockedIp ip = items.get(position);
        h.ip.setText(ip.ip != null ? ip.ip : "");
        h.until.setText(ip.blocked_until != null ? ip.blocked_until : "");
        h.reason.setText(ip.reason != null ? ip.reason : "");

        boolean selected = ip.ip != null && ip.ip.equals(selectedIp);
        h.card.setStrokeWidth(dp(h.card, selected ? 2 : 1));
        h.card.setStrokeColor(ContextCompat.getColor(h.card.getContext(), selected ? R.color.brand : R.color.stroke));

        h.unblock.setOnClickListener(v -> {
            if (listener != null) listener.onUnblock(ip);
        });
        h.itemView.setOnClickListener(v -> {
            if (listener == null) return;
            if (ip.ip == null || ip.ip.trim().isEmpty()) return;
            listener.onSelectBlockedIp(ip.ip.trim());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView ip;
        final TextView until;
        final TextView reason;
        final Button unblock;

        VH(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            ip = itemView.findViewById(R.id.ip);
            until = itemView.findViewById(R.id.until);
            reason = itemView.findViewById(R.id.reason);
            unblock = itemView.findViewById(R.id.unblockBtn);
        }
    }

    private static int dp(View v, int dp) {
        float density = v.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(dp * density));
    }
}

