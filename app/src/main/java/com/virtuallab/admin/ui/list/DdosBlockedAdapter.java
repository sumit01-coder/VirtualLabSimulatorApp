package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.virtuallab.admin.R;
import com.virtuallab.admin.model.DdosBlockedIp;

import java.util.ArrayList;
import java.util.List;

public final class DdosBlockedAdapter extends RecyclerView.Adapter<DdosBlockedAdapter.VH> {
    public interface Listener {
        void onUnblock(DdosBlockedIp ip);
    }

    private final Listener listener;
    private final List<DdosBlockedIp> items = new ArrayList<>();

    public DdosBlockedAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<DdosBlockedIp> next) {
        items.clear();
        if (next != null) items.addAll(next);
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
        h.unblock.setOnClickListener(v -> listener.onUnblock(ip));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView ip;
        final TextView until;
        final TextView reason;
        final Button unblock;

        VH( View itemView) {
            super(itemView);
            ip = itemView.findViewById(R.id.ip);
            until = itemView.findViewById(R.id.until);
            reason = itemView.findViewById(R.id.reason);
            unblock = itemView.findViewById(R.id.unblockBtn);
        }
    }
}

