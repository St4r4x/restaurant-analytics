package com.aflokkat.dto;

public class RegisterRequest {
    private String username;
    private String email;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    private String signupCode;

    public String getSignupCode() {
        return signupCode;
    }

    public void setSignupCode(String signupCode) {
        this.signupCode = signupCode;
    }
}