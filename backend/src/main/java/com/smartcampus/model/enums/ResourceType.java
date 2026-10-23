package com.smartcampus.model.enums;

/**
 * The kind of campus resource a booking refers to.
 * <p>
 * Because a booking can point at one of three different tables
 * (classrooms, laboratories, equipment) the association is polymorphic:
 * {@code resourceType} tells us which table {@code resourceId} belongs to.
 * </p>
 */
public enum ResourceType {

    CLASSROOM("Classroom"),
    LABORATORY("Laboratory"),
    EQUIPMENT("Equipment");

    private final String label;

    ResourceType(String label) {
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
