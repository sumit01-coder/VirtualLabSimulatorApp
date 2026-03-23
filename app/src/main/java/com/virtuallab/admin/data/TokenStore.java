package com.virtuallab.admin.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

public final class TokenStore {
    private static final String PREFS = "vl_admin_prefs";
    private static final String KEY_TOKEN = "adminToken";
    private static final String KEY_ADMIN_USERNAME = "adminUsername";
    private static final String KEY_ADMIN_EMAIL = "adminEmail";
    private static final String KEY_ADMIN_ROLE = "adminRole";

    private final SharedPreferences prefs;

    public TokenStore(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences legacy = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        SharedPreferences secured = null;
        try {
            String keyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            secured = EncryptedSharedPreferences.create(
                    PREFS + "_enc",
                    keyAlias,
                    app,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Throwable ignored) {
            // Fallback to legacy unencrypted prefs on older/broken devices.
        }

        if (secured != null) {
            // One-time migration from legacy prefs to encrypted prefs.
            if (secured.getString(KEY_TOKEN, null) == null && legacy.getString(KEY_TOKEN, null) != null) {
                secured.edit()
                        .putString(KEY_TOKEN, legacy.getString(KEY_TOKEN, null))
                        .putString(KEY_ADMIN_USERNAME, legacy.getString(KEY_ADMIN_USERNAME, null))
                        .putString(KEY_ADMIN_EMAIL, legacy.getString(KEY_ADMIN_EMAIL, null))
                        .putString(KEY_ADMIN_ROLE, legacy.getString(KEY_ADMIN_ROLE, null))
                        .apply();
                legacy.edit().clear().apply();
            }
            this.prefs = secured;
        } else {
            this.prefs = legacy;
        }
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
