package com.smartcampus.exception;

/** Thrown when an activity / event id does not exist -> HTTP 404. */
public class ActivityNotFoundException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public ActivityNotFoundException(Long id) {
        super("Campus activity not found with id " + id);
    }

    /** Overloaded constructor - lookup by activity name. */
    public ActivityNotFoundException(String activityName) {
        super("Campus activity not found: " + activityName);
    }
}
