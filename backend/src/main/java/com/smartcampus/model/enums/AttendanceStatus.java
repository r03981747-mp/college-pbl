package com.smartcampus.model.enums;

/** Daily attendance state for a student in one subject. */
public enum AttendanceStatus {

    PRESENT("Present"),
    ABSENT("Absent"),
    LATE("Late");

    private final String label;

    AttendanceStatus(String label) {
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
