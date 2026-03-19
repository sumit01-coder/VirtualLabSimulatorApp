package com.virtuallab.admin.model;

public final class TicketActionRequest {
    public final String action;
    public final int ticket_id;

    public TicketActionRequest(String action, int ticketId) {
        this.action = action;
        this.ticket_id = ticketId;
    }
}

