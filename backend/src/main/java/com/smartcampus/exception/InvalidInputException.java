package com.smartcampus.exception;

/**
 * Thrown when incoming data fails service level validation (empty required
 * field, malformed email, phone, out-of-range semester, bad date...).
 * Maps to HTTP <b>400 Bad Request</b>.
 */
public class InvalidInputException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public InvalidInputException(String message) {
        super(message);
    }

    /** Overloaded constructor - names the offending field. */
    public InvalidInputException(String field, String message) {
        super(field + ": " + message);
    }
}
