package com.virtuallab.admin.model;

public final class SettingsUpdateRequest {
    public final boolean maintenance_mode;
    public final boolean admin_email_2fa;

    public SettingsUpdateRequest(boolean maintenance_mode, boolean admin_email_2fa) {
        this.maintenance_mode = maintenance_mode;
        this.admin_email_2fa = admin_email_2fa;
    }
}

