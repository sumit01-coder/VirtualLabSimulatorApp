package com.virtuallab.admin.model;

public final class TicketActionRequest {
    public final String action;
    public final int ticket_id;
    public final String message;
    public final String assigned_admin;
    public final String admin_note;

    public TicketActionRequest(String action, int ticketId) {
        this.action = action;
        this.ticket_id = ticketId;
        this.message = null;
        this.assigned_admin = null;
        this.admin_note = null;
    }

    public TicketActionRequest(String action, int ticketId, String assignedAdmin, String adminNote) {
        this.action = action;
        this.ticket_id = ticketId;
        this.message = null;
        this.assigned_admin = assignedAdmin;
        this.admin_note = adminNote;
    }

    public TicketActionRequest(String action, int ticketId, String message) {
        this.action = action;
        this.ticket_id = ticketId;
        this.message = message;
        this.assigned_admin = null;
        this.admin_note = null;
    }
}

