package com.virtuallab.admin.model;

public final class UpdatesData {
    public LatestTicket latest_ticket;
    public LatestPractical latest_practical;
    public boolean maintenance_mode;

    public static final class LatestTicket {
        public int id;
        public String subject;
        public String status;
        public String created_at;
    }

    public static final class LatestPractical {
        public int id;
        public String title;
        public int lab_id;
    }
}
