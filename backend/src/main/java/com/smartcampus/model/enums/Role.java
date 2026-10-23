package com.smartcampus.model.enums;

/**
 * The three user roles supported by the Smart Campus system.
 * <p>
 * The role is always read from the database / authenticated session on the
 * backend.  The browser can never supply or override it.
 * </p>
 */
public enum Role {

    ADMIN("Administrator"),
    FACULTY("Faculty"),
    STUDENT("Student");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** Human readable name used in the UI and in CSV exports. */
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
