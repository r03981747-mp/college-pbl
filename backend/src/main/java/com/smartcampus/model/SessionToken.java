package com.smartcampus.model;

import java.time.LocalDateTime;

/**
 * An authentication session token issued by the login endpoint.
 * The token itself is a random UUID - the raw password never leaves the
 * server after login.
 */
public class SessionToken extends BaseEntity {

    private Long userId;
    private String token;
    private LocalDateTime expiresAt;

    public SessionToken() {
        super();
    }

    public SessionToken(Long id, Long userId, String token, LocalDateTime expiresAt) {
        super(id);
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    /** {@code true} when the session can still be used. */
    public boolean isValid() {
        return expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
