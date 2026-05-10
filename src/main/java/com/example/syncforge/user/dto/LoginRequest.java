package com.example.syncforge.user.dto;

public class LoginRequest {
    private String username;
    private String password;
    private String passwordHash;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getResolvedPassword() {
        if (passwordHash != null && !passwordHash.trim().isEmpty()) {
            return passwordHash;
        }
        return password;
    }

    // 兼容旧调用，后续可删除
    public String getResolvedPasswordHash() {
        return getResolvedPassword();
    }
}

