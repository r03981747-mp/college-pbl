package com.smartcampus.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials posted by the login form.
 * <p>The password is validated as "present" only - it is never logged and
 * never stored; it is verified against the BCrypt hash on the server.</p>
 *
 * <p>The identifier field is called {@code username} internally but the API
 * also accepts {@code email} (and {@code usernameOrEmail}) from the browser,
 * because the demo accounts are addressed by e-mail.</p>
 */
public class LoginRequest {

    @NotBlank(message = "Username or email is required")
    @JsonAlias({"email", "usernameOrEmail", "user"})
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    /** Optional "remember me" flag - only extends the session lifetime. */
    private boolean rememberMe;

    public LoginRequest() {
    }

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

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

    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }
}
