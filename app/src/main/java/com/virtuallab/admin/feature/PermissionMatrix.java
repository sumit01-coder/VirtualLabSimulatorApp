package com.virtuallab.admin.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PermissionMatrix {
    private PermissionMatrix() {}

    public enum Capability {
        VIEW_DASHBOARD,
        VIEW_TICKETS,
        CLOSE_TICKETS,
        ASSIGN_TICKETS,
        MANAGE_LABS,
        MANAGE_DEPARTMENTS,
        MANAGE_SETTINGS,
        MANAGE_DDOS,
        VIEW_AUDIT_LOGS,
        VIEW_API_HEALTH
    }

    public static boolean has(String role, Capability capability) {
        String r = role == null ? "" : role.trim().toLowerCase(Locale.US);
        if (r.equals("super_admin")) return true;

        switch (capability) {
            case VIEW_DASHBOARD:
            case VIEW_TICKETS:
            case VIEW_API_HEALTH:
            case MANAGE_LABS:
            case MANAGE_DDOS:
                return true;
            case CLOSE_TICKETS:
            case ASSIGN_TICKETS:
            case MANAGE_DEPARTMENTS:
            case MANAGE_SETTINGS:
            case VIEW_AUDIT_LOGS:
            default:
                return false;
        }
    }

    public static List<String> readableForRole(String role) {
        List<String> out = new ArrayList<>();
        for (Capability c : Capability.values()) {
            out.add(c.name() + ": " + (has(role, c) ? "ALLOW" : "DENY"));
        }
        return out;
    }
}
