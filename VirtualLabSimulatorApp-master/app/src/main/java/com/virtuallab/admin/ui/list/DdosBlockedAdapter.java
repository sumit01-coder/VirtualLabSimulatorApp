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
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    public void setSelectedIp(String ip) {
        selectedIp = ip != null ? ip : "";
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blocked_ip, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        DdosBlockedIp item = items.get(position);
        holder.ip.setText(item.ip != null ? item.ip : "");
        holder.until.setText(item.blocked_until != null ? item.blocked_until : "");
        holder.reason.setText(item.reason != null ? item.reason : "");

        boolean selected = item.ip != null && item.ip.equals(selectedIp);
        holder.card.setStrokeWidth(dp(holder.card, selected ? 2 : 1));
        holder.card.setStrokeColor(ContextCompat.getColor(holder.card.getContext(), selected ? R.color.ddos_blue : R.color.ddos_stroke));

        holder.unblock.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUnblock(item);
            }
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener == null || item.ip == null || item.ip.trim().isEmpty()) {
                return;
            }
            listener.onSelectBlockedIp(item.ip.trim());
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
        final MaterialButton unblock;

        VH(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            ip = itemView.findViewById(R.id.ip);
            until = itemView.findViewById(R.id.until);
            reason = itemView.findViewById(R.id.reason);
            unblock = itemView.findViewById(R.id.unblockBtn);
        }
    }

    private static int dp(View view, int value) {
        float density = view.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(value * density));
    }
}
