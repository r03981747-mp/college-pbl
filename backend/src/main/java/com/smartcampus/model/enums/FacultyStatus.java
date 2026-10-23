package com.smartcampus.model.enums;

/** Lifecycle status of a faculty member. */
public enum FacultyStatus {

    ACTIVE("Active"),
    ON_LEAVE("On Leave"),
    INACTIVE("Inactive");

    private final String label;

    FacultyStatus(String label) {
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
