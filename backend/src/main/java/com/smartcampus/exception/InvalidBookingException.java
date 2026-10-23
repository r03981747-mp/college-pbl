package com.smartcampus.exception;

/**
 * Thrown when a booking request is structurally invalid - missing resource,
 * bad date, end time before start time, past date, blank purpose... Maps to
 * HTTP <b>400 Bad Request</b>.
 */
public class InvalidBookingException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public InvalidBookingException(String message) {
        super(message);
    }

    /** Overloaded constructor - appends a hint to the given reason. */
    public InvalidBookingException(String reason, String hint) {
        super("Invalid booking: " + reason + (hint == null ? "" : ". " + hint));
    }
}
