package com.example.securesamvad.model;

public class User {

    private String uid;
    private String phone;
    private String name;
    private long   lastTimestamp;// ← NEW

    private String pubKey;



    public User() {}                  // Firebase needs empty ctor

    public User(String uid, String phone, String name) {
        this(uid, phone, name, 0);
    }

    public User(String uid, String phone, String name, long lastTimestamp) {
        this.uid  = uid;
        this.phone = phone;
        this.name  = name;
        this.lastTimestamp = lastTimestamp;
    }

    // getters
    public String getUid()  { return uid; }
    public String getPhone(){ return phone; }
    public String getName() { return name; }
    public long   getLastTimestamp() { return lastTimestamp; }

    // setter (only for timestamp)
    public void setLastTimestamp(long ts) { this.lastTimestamp = ts; }

    public String getPubKey() { return pubKey; }
    public void setPubKey(String k){ pubKey = k; }
}
