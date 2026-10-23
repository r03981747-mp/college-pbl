package com.smartcampus.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartcampus.model.enums.AccountStatus;
import com.smartcampus.model.enums.Role;

/**
 * A login account.
 *
 * <p><b>Security:</b> the {@code password} field holds a BCrypt <b>hash</b>,
 * never a plain-text password, and it is annotated with
 * {@link JsonIgnore} so it can never be serialised back to the browser -
 * not even by accident.</p>
 */
public class UserAccount extends BaseEntity {

    private String username;
    private String email;

    @JsonIgnore
    private String password;

    private String fullName;
    private Role role;
    private AccountStatus status;

    /** No-argument constructor (JDBC row mapping). */
    public UserAccount() {
        super();
        this.role = Role.STUDENT;
        this.status = AccountStatus.ACTIVE;
    }

    /** Partial constructor - identity only. */
    public UserAccount(Long id, String username, String fullName, Role role) {
        super(id);
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.status = AccountStatus.ACTIVE;
    }

    /** Full constructor. */
    public UserAccount(Long id, String username, String email, String password,
                       String fullName, Role role, AccountStatus status) {
        super(id);
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
    }

    /** {@code true} when the account is allowed to sign in. */
    public boolean canLogin() {
        return status != null && status.isEnabled();
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

    @JsonIgnore
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

    @Override
    public String toString() {
        return "UserAccount{" + username + ", " + role + "}";
    }
}
