package com.virtuallab.admin.feature;

import com.virtuallab.admin.Config;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ApiHealthChecker {
    private ApiHealthChecker() {}

    public static final class Row {
        public final String endpoint;
        public final int code;
        public final long latencyMs;
        public final boolean ok;

        public Row(String endpoint, int code, long latencyMs, boolean ok) {
            this.endpoint = endpoint;
            this.code = code;
            this.latencyMs = latencyMs;
            this.ok = ok;
        }
    }

    public static List<Row> run() {
        String[] endpoints = new String[] {
                "dashboard.php",
                "tickets.php?status=all",
                "users.php",
                "practicals.php",
                "app_update.php?platform=android&current_version=0.0.0",
                "ddos.php?action=overview"
        };
        OkHttpClient c = new OkHttpClient.Builder().build();
        List<Row> out = new ArrayList<>();
        for (String e : endpoints) {
            long s = System.currentTimeMillis();
            int code = 0;
            boolean ok = false;
            try {
                Request req = new Request.Builder().url(Config.API_BASE_URL + e).get().build();
                try (Response r = c.newCall(req).execute()) {
                    code = r.code();
                    ok = code >= 200 && code < 500;
                }
            } catch (Exception ignored) {
                ok = false;
            }
            long elapsed = Math.max(1, System.currentTimeMillis() - s);
            out.add(new Row(e, code, elapsed, ok));
        }
        return out;
    }
}
