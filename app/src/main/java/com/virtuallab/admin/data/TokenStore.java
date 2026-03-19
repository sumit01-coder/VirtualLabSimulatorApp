package com.virtuallab.admin.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class TokenStore {
    private static final String PREFS = "vl_admin_prefs";
    private static final String KEY_TOKEN = "adminToken";
    private static final String KEY_ADMIN_USERNAME = "adminUsername";
    private static final String KEY_ADMIN_EMAIL = "adminEmail";
    private static final String KEY_ADMIN_ROLE = "adminRole";

    private final SharedPreferences prefs;

    public TokenStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, String username, String email, String role) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_ADMIN_USERNAME, username)
                .putString(KEY_ADMIN_EMAIL, email)
                .putString(KEY_ADMIN_ROLE, role)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public boolean hasToken() {
        String t = getToken();
        return t != null && !t.isEmpty();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    public String getUsername() {
        return prefs.getString(KEY_ADMIN_USERNAME, "Admin");
    }

    public String getEmail() {
        return prefs.getString(KEY_ADMIN_EMAIL, "");
    }

    public String getRole() {
        return prefs.getString(KEY_ADMIN_ROLE, "");
    }
}
