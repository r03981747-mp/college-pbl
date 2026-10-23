package com.smartcampus.exception;

/** Thrown when a student primary key / USN does not exist -> HTTP 404. */
public class StudentNotFoundException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public StudentNotFoundException(Long id) {
        super("Student not found with id " + id);
    }

    /** Overloaded constructor - lookup by USN instead of id. */
    public StudentNotFoundException(String usn) {
        super("Student not found with USN " + usn);
    }
}
