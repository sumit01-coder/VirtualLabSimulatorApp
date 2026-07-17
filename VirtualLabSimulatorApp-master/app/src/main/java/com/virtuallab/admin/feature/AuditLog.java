package com.virtuallab.admin.feature;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class AuditLog {
    private static final String PREFS = "vl_audit";
    private static final String KEY_ROWS = "rows";
    private static final int MAX_ROWS = 500;

    private AuditLog() {}

    public static final class Entry {
        public final long ts;
        public final String actor;
        public final String action;
        public final String detail;

        public Entry(long ts, String actor, String action, String detail) {
            this.ts = ts;
            this.actor = actor != null ? actor : "";
            this.action = action != null ? action : "";
            this.detail = detail != null ? detail : "";
        }

        public String tsLabel() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(ts));
        }
    }

    public static void write(Context context, String actor, String action, String detail) {
        if (context == null) return;
        List<Entry> rows = read(context);
        rows.add(new Entry(System.currentTimeMillis(), actor, action, detail));
        while (rows.size() > MAX_ROWS) rows.remove(0);
        persist(context, rows);
    }

    public static List<Entry> read(Context context) {
        List<Entry> out = new ArrayList<>();
        if (context == null) return out;
        SharedPreferences p = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY_ROWS, "");
        if (raw == null || raw.trim().isEmpty()) return out;

        String[] lines = raw.split("\n");
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) continue;
            String[] parts = line.split("\\|", 4);
            if (parts.length < 4) continue;
            long ts;
            try {
                ts = Long.parseLong(parts[0]);
            } catch (Exception ignored) {
                continue;
            }
            out.add(new Entry(ts, parts[1], parts[2], parts[3]));
        }
        return out;
    }

    public static void clear(Context context) {
        if (context == null) return;
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ROWS)
                .apply();
    }

    private static void persist(Context context, List<Entry> rows) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : rows) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.ts).append('|')
                    .append(sanitize(e.actor)).append('|')
                    .append(sanitize(e.action)).append('|')
                    .append(sanitize(e.detail));
        }
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ROWS, sb.toString())
                .apply();
    }

    private static String sanitize(String v) {
        if (v == null) return "";
        return v.replace("|", "/").replace("\n", " ").trim();
    }
}
