package com.smartcampus.exception;

/** Assessment / assignment / submission not found (HTTP 404). */
public class AcademicNotFoundException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public AcademicNotFoundException(String label, Long id) {
        super(label + " not found with id " + id);
    }

    public AcademicNotFoundException(String message) {
        super(message);
    }
}
