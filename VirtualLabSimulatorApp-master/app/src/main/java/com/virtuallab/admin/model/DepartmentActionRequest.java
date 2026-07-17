package com.virtuallab.admin.model;

public final class DepartmentActionRequest {
    public String action;
    public int id;
    public String name;
    public String description;
    public String icon_class;

    public static DepartmentActionRequest add(String name, String description, String iconClass) {
        DepartmentActionRequest r = new DepartmentActionRequest();
        r.action = "add";
        r.name = name;
        r.description = description;
        r.icon_class = iconClass;
        return r;
    }

    public static DepartmentActionRequest edit(int id, String name, String description, String iconClass) {
        DepartmentActionRequest r = new DepartmentActionRequest();
        r.action = "edit";
        r.id = id;
        r.name = name;
        r.description = description;
        r.icon_class = iconClass;
        return r;
    }

    public static DepartmentActionRequest delete(int id) {
        DepartmentActionRequest r = new DepartmentActionRequest();
        r.action = "delete";
        r.id = id;
        return r;
    }
}

