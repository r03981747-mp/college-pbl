package com.smartcampus.model.enums;

/** Lifecycle status of a course. */
public enum CourseStatus {

    ACTIVE("Active"),
    INACTIVE("Inactive");

    private final String label;

    CourseStatus(String label) {
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
