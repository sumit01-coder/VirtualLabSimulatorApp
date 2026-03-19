package com.virtuallab.admin.model;

public final class LoginRequest {
    public final String username;
    public final String password;

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}

