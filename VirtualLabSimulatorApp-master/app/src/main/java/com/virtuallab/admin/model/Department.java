package com.virtuallab.admin.model;

public final class Department {
    public int id;
    public String name;
    public String description;
    public String icon_class;

    @Override
    public String toString() {
        return name != null ? name : "";
    }
}
