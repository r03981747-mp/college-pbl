package com.smartcampus.model.enums;

/** Category of a campus activity / event. */
public enum ActivityCategory {

    TECHNICAL("Technical"),
    CULTURAL("Cultural"),
    SPORTS("Sports"),
    WORKSHOP("Workshop"),
    SEMINAR("Seminar"),
    CLUB_ACTIVITY("Club Activity"),
    OTHER("Other");

    private final String label;

    ActivityCategory(String label) {
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
