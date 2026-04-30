package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.model.DdosRecentRequest;

import java.util.ArrayList;
import java.util.List;

public final class DdosRecentAdapter extends RecyclerView.Adapter<DdosRecentAdapter.VH> {
    public interface Listener {
        void onSelectIp(String ip);
    }

    private final List<DdosRecentRequest> items = new ArrayList<>();
    private final Listener listener;
    private String selectedIp = "";

    public DdosRecentAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setSelectedIp(String ip) {
        selectedIp = ip != null ? ip : "";
        notifyDataSetChanged();
    }

    public void submit(List<DdosRecentRequest> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ddos_recent, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        DdosRecentRequest item = items.get(position);
        holder.time.setText(item.time_str != null ? item.time_str : "");
        holder.ip.setText(item.ip != null ? item.ip : "");
        holder.method.setText(item.method != null ? item.method : "");
        holder.endpoint.setText(item.endpoint != null ? item.endpoint : "");
        holder.errorDot.setBackgroundResource(item.is_error == 1 ? R.drawable.bg_status_dot_danger : R.drawable.bg_status_dot_success);

        boolean selected = item.ip != null && item.ip.equals(selectedIp);
        holder.card.setStrokeWidth(dp(holder.card, selected ? 2 : 1));
        holder.card.setStrokeColor(ContextCompat.getColor(holder.card.getContext(), selected ? R.color.ddos_blue : R.color.ddos_stroke));

        String method = item.method != null ? item.method.trim().toUpperCase() : "";
        if ("POST".equals(method)) {
            holder.method.setBackgroundResource(R.drawable.bg_ddos_chip_red);
            holder.method.setTextColor(ContextCompat.getColor(holder.method.getContext(), R.color.ddos_red));
        } else {
            holder.method.setBackgroundResource(R.drawable.bg_ddos_chip_blue);
            holder.method.setTextColor(ContextCompat.getColor(holder.method.getContext(), R.color.ddos_blue));
        }

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
        final TextView time;
        final TextView ip;
        final TextView method;
        final TextView endpoint;
        final View errorDot;

        VH(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            time = itemView.findViewById(R.id.time);
            ip = itemView.findViewById(R.id.ip);
            method = itemView.findViewById(R.id.method);
            endpoint = itemView.findViewById(R.id.endpoint);
            errorDot = itemView.findViewById(R.id.errorDot);
        }
    }

    private static int dp(View view, int value) {
        float density = view.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(value * density));
    }
}
