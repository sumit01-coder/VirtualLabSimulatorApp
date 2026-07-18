package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.sumit.virtuallabadmin.v29.R;
import com.virtuallab.admin.model.Ticket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TicketsAdapter extends RecyclerView.Adapter<TicketsAdapter.VH> {
    public interface Listener {
        void onResolve(Ticket ticket);
        void onReply(Ticket ticket);
        void onOpen(Ticket ticket);
        void onSelectionChanged(int count);
    }

    private final Listener listener;
    private final List<Ticket> items = new ArrayList<>();
    private final Set<Integer> selectedIds = new HashSet<>();

    public TicketsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Ticket> next) {
        items.clear();
        if (next != null) items.addAll(next);
        selectedIds.clear();
        listener.onSelectionChanged(0);
        notifyDataSetChanged();
    }

    public int getTotalCount() {
        return items.size();
    }

    public List<Ticket> getSelectedItems() {
        List<Ticket> out = new ArrayList<>();
        for (Ticket t : items) {
            if (t != null && selectedIds.contains(t.id)) out.add(t);
        }
        return out;
    }

    public void clearSelection() {
        selectedIds.clear();
        listener.onSelectionChanged(0);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ticket, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Ticket t = items.get(position);

        String subject = t.subject != null ? t.subject : "(no subject)";
        String sender = t.sender_name != null ? t.sender_name : "Unknown";
        String email = t.sender_email != null ? t.sender_email : "";
        String statusRaw = t.status != null ? t.status.trim() : "";
        String status = statusRaw.isEmpty() ? "" : statusRaw.toUpperCase(Locale.US);

        h.subject.setText(subject);
        h.sender.setText(sender);
        h.email.setText(email);
        h.statusChip.setText(status);
        h.date.setText(t.created_at != null ? t.created_at : "");

        String initial = "T";
        if (sender != null && !sender.trim().isEmpty()) {
            initial = String.valueOf(sender.trim().charAt(0)).toUpperCase(Locale.US);
        } else if (email != null && !email.trim().isEmpty()) {
            initial = String.valueOf(email.trim().charAt(0)).toUpperCase(Locale.US);
        }
        h.avatar.setText(initial);

        applyStatusStyle(h, statusRaw);

        boolean canResolve = t.status != null && !t.status.equalsIgnoreCase("closed");
        h.resolve.setVisibility(canResolve ? View.VISIBLE : View.GONE);
        h.resolve.setOnClickListener(v -> listener.onResolve(t));
        
        h.reply.setVisibility(canResolve ? View.VISIBLE : View.GONE);
        h.reply.setOnClickListener(v -> listener.onReply(t));
        
        h.selectBox.setOnCheckedChangeListener(null);
        h.selectBox.setChecked(selectedIds.contains(t.id));
        h.selectBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) selectedIds.add(t.id);
            else selectedIds.remove(t.id);
            listener.onSelectionChanged(selectedIds.size());
        });

        h.itemView.setOnClickListener(v -> listener.onOpen(t));
        h.itemView.setOnLongClickListener(v -> {
            boolean checked = !h.selectBox.isChecked();
            h.selectBox.setChecked(checked);
            return true;
        });
    }

    private static void applyStatusStyle(VH h, String statusRaw) {
        String s = statusRaw != null ? statusRaw.trim().toLowerCase(Locale.US) : "";
        int chipBg;
        int dotBg;
        int chipText;

        switch (s) {
            case "pending":
                chipBg = R.drawable.bg_chip_warn;
                dotBg = R.drawable.bg_status_dot_warn;
                chipText = R.color.warn;
                break;
            case "closed":
                chipBg = R.drawable.bg_chip_muted;
                dotBg = R.drawable.bg_status_dot_muted;
                chipText = R.color.text_muted;
                break;
            case "open":
            default:
                chipBg = R.drawable.bg_chip_brand;
                dotBg = R.drawable.bg_status_dot_brand;
                chipText = R.color.brand;
                break;
        }

        h.statusDot.setBackgroundResource(dotBg);
        h.statusChip.setBackgroundResource(chipBg);
        h.statusChip.setTextColor(ContextCompat.getColor(h.itemView.getContext(), chipText));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView avatar;
        final View statusDot;
        final TextView subject;
        final TextView sender;
        final TextView email;
        final TextView statusChip;
        final TextView date;
        final MaterialButton reply;
        final MaterialButton resolve;
        final CheckBox selectBox;

        VH(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatar);
            statusDot = itemView.findViewById(R.id.statusDot);
            subject = itemView.findViewById(R.id.subject);
            sender = itemView.findViewById(R.id.sender);
            email = itemView.findViewById(R.id.email);
            statusChip = itemView.findViewById(R.id.statusChip);
            date = itemView.findViewById(R.id.date);
            reply = itemView.findViewById(R.id.replyBtn);
            resolve = itemView.findViewById(R.id.resolveBtn);
            selectBox = itemView.findViewById(R.id.selectBox);
        }
    }
}

