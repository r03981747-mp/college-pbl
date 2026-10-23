package com.smartcampus.model.enums;

/** Lifecycle status of a campus activity / event. */
public enum ActivityStatus {

    SCHEDULED("Scheduled"),
    ONGOING("Ongoing"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String label;

    ActivityStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
