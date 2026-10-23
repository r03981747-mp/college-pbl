package com.smartcampus.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.smartcampus.model.enums.AccountStatus;
import com.smartcampus.model.enums.Role;

/**
 * Payload used by an administrator to create or edit a login account.
 *
 * <p>The password is only ever accepted here - it is hashed with BCrypt by
 * {@code UserService} before touching the database and is never returned.</p>
 */
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 80, message = "Username must be 3-80 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    private String email;

    /** Required when creating; leave blank when editing to keep the old one. */
    @Size(min = 6, max = 72, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 120, message = "Full name must be at most 120 characters")
    private String fullName;

    @NotNull(message = "Role is required")
    private Role role;

    private AccountStatus status = AccountStatus.ACTIVE;

    public CreateUserRequest() {
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
