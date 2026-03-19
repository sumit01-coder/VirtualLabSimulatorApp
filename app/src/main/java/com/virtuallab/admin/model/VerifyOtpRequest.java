package com.virtuallab.admin.model;

public class VerifyOtpRequest {
    public String username;
    public String temp_token;
    public String otp;

    public VerifyOtpRequest(String username, String temp_token, String otp) {
        this.username = username;
        this.temp_token = temp_token;
        this.otp = otp;
    }
}
