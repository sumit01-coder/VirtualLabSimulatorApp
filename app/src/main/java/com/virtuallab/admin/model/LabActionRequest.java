package com.virtuallab.admin.model;

public final class LabActionRequest {
    public String action;
    public int id;
    public String name;
    public String subject;
    public String topics;
    public String description;
    public int department_id;

    public static LabActionRequest add(String name, String subject, String topics, String description, int departmentId) {
        LabActionRequest r = new LabActionRequest();
        r.action = "add";
        r.name = name;
        r.subject = subject;
        r.topics = topics;
        r.description = description;
        r.department_id = departmentId;
        return r;
    }

    public static LabActionRequest edit(int id, String name, String subject, String topics, String description, int departmentId) {
        LabActionRequest r = new LabActionRequest();
        r.action = "edit";
        r.id = id;
        r.name = name;
        r.subject = subject;
        r.topics = topics;
        r.description = description;
        r.department_id = departmentId;
        return r;
    }

    public static LabActionRequest delete(int id) {
        LabActionRequest r = new LabActionRequest();
        r.action = "delete";
        r.id = id;
        return r;
    }
}

