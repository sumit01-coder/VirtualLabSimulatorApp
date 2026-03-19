package com.virtuallab.admin.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.virtuallab.admin.R;
import com.virtuallab.admin.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.VH> {
    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    private final List<User> all = new ArrayList<>();
    private final List<User> visible = new ArrayList<>();
    private final OnUserClickListener listener;
    private String query = "";

    public UsersAdapter(OnUserClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<User> next) {
        all.clear();
        if (next != null) all.addAll(next);
        applyFilter();
    }

    public void setQuery(String q) {
        query = q != null ? q.trim() : "";
        applyFilter();
    }

    public int getTotalCount() {
        return all.size();
    }

    public int getVisibleCount() {
        return visible.size();
    }

    public int getBlockedCount() {
        int n = 0;
        for (User u : all) {
            if (u != null && u.status != null && u.status.trim().equalsIgnoreCase("blocked")) n++;
        }
        return n;
    }

    public int getActiveCount() {
        return Math.max(0, getTotalCount() - getBlockedCount());
    }

    private void applyFilter() {
        visible.clear();
        if (query.isEmpty()) {
            visible.addAll(all);
        } else {
            String needle = query.toLowerCase(Locale.US);
            for (User u : all) {
                if (matches(u, needle)) visible.add(u);
            }
        }
        notifyDataSetChanged();
    }

    private static boolean matches(User u, String needle) {
        return contains(u.full_name, needle)
                || contains(u.email, needle)
                || contains(u.unique_id, needle)
                || contains(u.username, needle)
                || contains(u.role, needle)
                || contains(u.institution, needle)
                || contains(u.status, needle)
                || contains(u.department, needle)
                || contains(u.current_year, needle);
    }

    private static boolean contains(String v, String needle) {
        if (v == null) return false;
        return v.toLowerCase(Locale.US).contains(needle);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        User u = visible.get(position);

        String name = u.full_name != null ? u.full_name : "Unknown";
        String email = u.email != null ? u.email : "";
        h.name.setText(name);
        h.email.setText(email);
        h.uid.setText(u.unique_id != null ? u.unique_id : "");

        boolean isBlocked = u.status != null && u.status.trim().equalsIgnoreCase("blocked");
        h.itemView.setAlpha(isBlocked ? 0.75f : 1.0f);
        h.statusDot.setBackgroundResource(isBlocked ? R.drawable.bg_status_dot_danger : R.drawable.bg_status_dot_success);
        h.statusChip.setBackgroundResource(isBlocked ? R.drawable.bg_chip_danger : R.drawable.bg_chip_success);
        h.statusChip.setText(isBlocked ? "BLOCKED" : "ACTIVE");
        h.statusChip.setTextColor(ContextCompat.getColor(h.itemView.getContext(), isBlocked ? R.color.danger : R.color.success));

        String initial = "U";
        if (!name.trim().isEmpty()) {
            initial = String.valueOf(name.trim().charAt(0)).toUpperCase(Locale.US);
        } else if (!email.trim().isEmpty()) {
            initial = String.valueOf(email.trim().charAt(0)).toUpperCase(Locale.US);
        }
        h.avatar.setText(initial);

        String meta = "";
        if (u.department != null) meta += u.department;
        if (u.current_year != null && !u.current_year.isEmpty()) meta += " \u2022 Year " + u.current_year;
        h.meta.setText(meta);

        String extra = "";
        if (u.institution != null && !u.institution.isEmpty()) extra += u.institution;
        if (u.tokens > 0) {
            if (!extra.isEmpty()) extra += " \u2022 ";
            extra += "Tokens: " + u.tokens;
        }
        h.extra.setText(extra);
        h.extra.setVisibility(extra.isEmpty() ? View.GONE : View.VISIBLE);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onUserClick(u);
        });
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView avatar;
        final TextView name;
        final TextView email;
        final TextView meta;
        final TextView extra;
        final TextView uid;
        final View statusDot;
        final TextView statusChip;

        VH(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatar);
            name = itemView.findViewById(R.id.name);
            email = itemView.findViewById(R.id.email);
            meta = itemView.findViewById(R.id.meta);
            extra = itemView.findViewById(R.id.extra);
            uid = itemView.findViewById(R.id.uid);
            statusDot = itemView.findViewById(R.id.statusDot);
            statusChip = itemView.findViewById(R.id.statusChip);
        }
    }
}
