package com.virtuallab.admin.model;

public final class LoginResponseData {
    public boolean require_2fa;
    public String temp_token;
    public String masked_email;
    public int otp_expires_in;
    public int resend_cooldown;
    public String token;
    public Admin admin;
}
