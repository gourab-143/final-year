package com.example.securesamvad.model;

public class Contact {
    private String name;
    private String phone;
    private String uid;          // null if not registered
    private boolean registered;  // true if uid != null

    public Contact() { }

    public Contact(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    // getters & setters
    public String getName()        { return name; }
    public String getPhone()       { return phone; }
    public String getUid()         { return uid; }
    public boolean isRegistered()  { return registered; }

    public void setUid(String uid)             { this.uid = uid; }
    public void setRegistered(boolean reg)     { this.registered = reg; }
}
