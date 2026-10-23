package com.smartcampus.model.enums;

/**
 * Availability status shared by every campus resource
 * (classroom, laboratory and equipment).
 * <p>
 * Only {@link #ACTIVE} resources may be booked.
 * </p>
 */
public enum ResourceStatus {

    ACTIVE("Active"),
    MAINTENANCE("Under Maintenance"),
    INACTIVE("Inactive");

    private final String label;

    ResourceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Convenience predicate used by the booking service. */
    public boolean isBookable() {
        return this == ACTIVE;
    }

    @Override
    public String toString() {
        return label;
    }
}
