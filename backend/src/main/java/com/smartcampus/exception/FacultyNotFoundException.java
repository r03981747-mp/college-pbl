package com.smartcampus.exception;

/** Thrown when a faculty primary key / employee id does not exist -> HTTP 404. */
public class FacultyNotFoundException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public FacultyNotFoundException(Long id) {
        super("Faculty member not found with id " + id);
    }

    /** Overloaded constructor - lookup by employee id. */
    public FacultyNotFoundException(String employeeId) {
        super("Faculty member not found with employee id " + employeeId);
    }
}
