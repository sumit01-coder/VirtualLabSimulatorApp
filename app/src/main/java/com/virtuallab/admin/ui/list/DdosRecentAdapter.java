package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.sumit.virtuallabadmin.v28.R;
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
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ddos_recent, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DdosRecentRequest r = items.get(position);
        h.time.setText(r.time_str != null ? r.time_str : "");
        h.ip.setText(r.ip != null ? r.ip : "");
        h.method.setText(r.method != null ? r.method : "");
        h.endpoint.setText(r.endpoint != null ? r.endpoint : "");
        h.errorDot.setVisibility(r.is_error == 1 ? View.VISIBLE : View.GONE);

        boolean selected = r.ip != null && r.ip.equals(selectedIp);
        h.card.setStrokeWidth(dp(h.card, selected ? 2 : 1));
        h.card.setStrokeColor(ContextCompat.getColor(h.card.getContext(), selected ? R.color.brand : R.color.stroke));

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

    private static int dp(View v, int dp) {
        float density = v.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(dp * density));
    }
}

