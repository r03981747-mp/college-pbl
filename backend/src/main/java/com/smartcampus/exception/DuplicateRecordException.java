package com.smartcampus.exception;

/**
 * Thrown when a unique business key already exists - duplicate USN,
 * duplicate employee id, duplicate course code, duplicate room number...
 * Maps to HTTP <b>409 Conflict</b>.
 */
public class DuplicateRecordException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public DuplicateRecordException(String message) {
        super(message);
    }

    /** Overloaded constructor - builds the message from field + value. */
    public DuplicateRecordException(String field, String value) {
        super(field + " '" + value + "' already exists. Please use a different value.");
    }
}
