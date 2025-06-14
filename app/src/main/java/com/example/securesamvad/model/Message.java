package com.example.securesamvad.model;

public class Message {
    private String messageId;
    private String senderId;
    private String message;
    private long timestamp;

    public Message() {}

    public Message(String messageId, String senderId, String message, long timestamp) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
