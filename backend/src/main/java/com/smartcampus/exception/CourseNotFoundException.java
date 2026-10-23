package com.smartcampus.exception;

/** Thrown when a course primary key / course code does not exist -> HTTP 404. */
public class CourseNotFoundException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public CourseNotFoundException(Long id) {
        super("Course not found with id " + id);
    }

    /** Overloaded constructor - lookup by course code. */
    public CourseNotFoundException(String courseCode) {
        super("Course not found with code " + courseCode);
    }
}
