package com.smartcampus.model.enums;

/** Lifecycle status of a student record. */
public enum StudentStatus {

    ACTIVE("Active"),
    INACTIVE("Inactive"),
    COMPLETED("Completed");

    private final String label;

    StudentStatus(String label) {
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
