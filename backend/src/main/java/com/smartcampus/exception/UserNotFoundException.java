package com.smartcampus.exception;

/** Thrown when a login account does not exist -> HTTP 404. */
public class UserNotFoundException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public UserNotFoundException(Long id) {
        super("User account not found with id " + id);
    }

    /** Overloaded constructor - lookup by username or email. */
    public UserNotFoundException(String username) {
        super("User account not found: " + username);
    }
}
