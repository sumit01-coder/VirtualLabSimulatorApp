package com.virtuallab.admin.feature;

import android.content.Context;

import com.google.gson.Gson;
import com.virtuallab.admin.Config;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class RemoteConfigManager {
    private static final String CACHE_KEY = "remote_config_cache";
    private static final Gson GSON = new Gson();

    private RemoteConfigManager() {}

    public static final class Data {
        public boolean maintenance_banner = false;
        public String banner_text = "";
        public boolean realtime_alerts = true;
    }

    public static Data readCached(Context context) {
        Data d = OfflineCache.getObject(context, CACHE_KEY, Data.class);
        return d != null ? d : new Data();
    }

    public static Data sync(Context context) throws IOException {
        String defaultUrl = Config.API_BASE_URL + "remote_config.json";
        String url = FeaturePrefs.getRemoteConfigUrl(context, defaultUrl);

        OkHttpClient c = new OkHttpClient.Builder().build();
        Request req = new Request.Builder().url(url).get().build();
        try (Response r = c.newCall(req).execute()) {
            if (!r.isSuccessful() || r.body() == null) {
                throw new IOException("HTTP " + r.code());
            }
            String raw = r.body().string();
            Data d = GSON.fromJson(raw, Data.class);
            if (d == null) d = new Data();
            OfflineCache.putObject(context, CACHE_KEY, d);
            FeaturePrefs.setRealtimeAlertsEnabled(context, d.realtime_alerts);
            return d;
        }
    }
}
