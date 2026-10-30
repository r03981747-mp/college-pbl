package com.smartcampus.model.enums;

/**
 * Types of assessment supported by the academic module.  The database stores
 * the enum name, so adding a value here requires a matching CHECK update.
 */
public enum AssessmentType {

    QUIZ("Quiz"),
    ASSIGNMENT("Assignment"),
    MIDTERM("Midterm"),
    PRACTICAL("Practical"),
    PROJECT("Project"),
    FINAL("Final"),
    OTHER("Other");

    private final String label;

    AssessmentType(String label) {
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
