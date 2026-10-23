package com.smartcampus.dto;

import com.smartcampus.model.enums.AccountStatus;
import com.smartcampus.model.enums.Role;

/**
 * The ONLY user representation that ever reaches the browser.
 *
 * <p>Notice there is no password field at all - the BCrypt hash stays on the
 * server.</p>
 */
public class UserInfo {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private Role role;
    private AccountStatus status;

    public UserInfo() {
    }

    public UserInfo(Long id, String username, String email, String fullName,
                    Role role, AccountStatus status) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }
}
