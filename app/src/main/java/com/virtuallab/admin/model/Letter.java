package com.virtuallab.admin.model;

import com.google.gson.annotations.SerializedName;

public class Letter {
    @SerializedName("letter_id")
    public String letterId;

    @SerializedName("letter_date")
    public String letterDate;

    public String subject;

    @SerializedName("sender_name")
    public String senderName;

    @SerializedName("recipient_name")
    public String recipientName;

    @SerializedName("recipient_email")
    public String recipientEmail;

    @SerializedName("created_at")
    public String createdAt;
}
