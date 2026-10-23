package com.smartcampus.security;

import java.util.Arrays;

import com.smartcampus.model.enums.Role;

/**
 * The identity of the caller, resolved from a valid session token.
 *
 * <p>It is created by {@link AuthInterceptor} from the <b>database</b> - the
 * browser never supplies a role, so it cannot be forged.</p>
 *
 * <p><b>OOP concepts:</b> <i>record</i> (immutable value object with
 * auto-generated accessors) and <i>encapsulation</i>.</p>
 */
public record AuthenticatedUser(Long id, String username, String fullName,
                                String email, Role role) {

    /** {@code true} when the user holds one of the supplied roles. */
    public boolean hasRole(Role... roles) {
        return roles != null && Arrays.stream(roles).anyMatch(role -> role == this.role);
    }

    /** {@code true} for an administrator - short cut used by several services. */
    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    @Override
    public String toString() {
        return username + " (" + role + ")";
    }
}
