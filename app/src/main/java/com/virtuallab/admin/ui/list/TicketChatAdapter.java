package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.model.TicketMessage;

import java.util.ArrayList;
import java.util.List;

public class TicketChatAdapter extends RecyclerView.Adapter<TicketChatAdapter.VH> {

    private static final int TYPE_ADMIN = 1;
    private static final int TYPE_USER = 2;

    private final List<TicketMessage> items = new ArrayList<>();

    public void submit(List<TicketMessage> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void addMessage(TicketMessage msg) {
        if (msg != null) {
            items.add(msg);
            notifyItemInserted(items.size() - 1);
        }
    }

    @Override
    public int getItemViewType(int position) {
        TicketMessage m = items.get(position);
        if (m != null && "admin".equalsIgnoreCase(m.author_type)) {
            return TYPE_ADMIN;
        }
        return TYPE_USER;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = (viewType == TYPE_ADMIN) ? R.layout.item_chat_admin : R.layout.item_chat_user;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TicketMessage m = items.get(position);
        if (m == null) return;
        
        holder.name.setText(m.author_name != null ? m.author_name : "Unknown");
        holder.message.setText(m.message != null ? m.message : "");
        holder.time.setText(m.created_at != null ? m.created_at : "");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView message;
        final TextView time;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.chatName);
            message = itemView.findViewById(R.id.chatMessage);
            time = itemView.findViewById(R.id.chatTime);
        }
    }
}
