package com.smartcampus.model.enums;

/**
 * Lifecycle of a resource booking.
 * <p>
 * Only {@link #PENDING} and {@link #APPROVED} bookings block a time slot;
 * rejected / cancelled / completed bookings release it.
 * </p>
 */
public enum BookingStatus {

    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled"),
    COMPLETED("Completed");

    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Does this status occupy the requested time slot? */
    public boolean blocksSlot() {
        return this == PENDING || this == APPROVED;
    }

    @Override
    public String toString() {
        return label;
    }
}
