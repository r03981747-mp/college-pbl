package com.smartcampus.model.enums;

/** Type of a classroom / room. */
public enum RoomType {

    CLASSROOM("Classroom"),
    SEMINAR_HALL("Seminar Hall"),
    AUDITORIUM("Auditorium"),
    CONFERENCE_ROOM("Conference Room");

    private final String label;

    RoomType(String label) {
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
