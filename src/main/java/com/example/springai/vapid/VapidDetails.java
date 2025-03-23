package com.example.springai.vapid;

public class VapidDetails {
    private final String subject;
    private final String publicKey;
    private final String privateKey;

    public VapidDetails(String subject, String publicKey, String privateKey) {
        this.subject = subject;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    // Getters
    public String getSubject() { return subject; }
    public String getPublicKey() { return publicKey; }
    public String getPrivateKey() { return privateKey; }
}