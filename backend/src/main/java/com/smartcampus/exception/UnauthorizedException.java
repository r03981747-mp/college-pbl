package com.smartcampus.exception;

/**
 * Authentication failure - no token, invalid/expired token, unknown user or
 * wrong password.  Maps to HTTP <b>401 Unauthorized</b>.
 *
 * <p>Notice the login message is deliberately generic
 * ("Invalid username or password") so the API never reveals whether an
 * account exists.</p>
 */
public class UnauthorizedException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedException(String message) {
        super(message);
    }

    /** Overloaded constructor - default generic message. */
    public UnauthorizedException() {
        this("Invalid username or password");
    }
}
