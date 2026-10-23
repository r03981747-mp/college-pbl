package com.smartcampus.exception;

import java.time.LocalTime;

import com.smartcampus.model.enums.ResourceType;

/**
 * Thrown when a booking request overlaps an existing booking of the same
 * resource on the same day.  Maps to HTTP <b>409 Conflict</b>.
 *
 * <p>This exception is raised inside the <b>synchronised critical section</b>
 * of {@code BookingServiceImpl}, so two threads racing for the same slot can
 * never both succeed.</p>
 *
 * <p>The object carries the conflicting time window so the API can answer with
 * a precise, user friendly sentence, e.g.</p>
 * <blockquote>
 *   Booking conflict: Laboratory LAB-02 is already booked from 10:00 to 12:00.
 * </blockquote>
 */
public class BookingConflictException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    private final transient ResourceType resourceType;
    private final String resourceName;
    private final String conflictingBookingRef;
    private final LocalTime conflictStart;
    private final LocalTime conflictEnd;

    public BookingConflictException(String message) {
        super(message);
        this.resourceType = null;
        this.resourceName = null;
        this.conflictingBookingRef = null;
        this.conflictStart = null;
        this.conflictEnd = null;
    }

    public BookingConflictException(ResourceType resourceType, String resourceName,
                                    String conflictingBookingRef,
                                    LocalTime conflictStart, LocalTime conflictEnd) {
        super("Booking conflict: " + resourceName + " is already booked from "
                + conflictStart + " to " + conflictEnd
                + (conflictingBookingRef == null ? "" : " (" + conflictingBookingRef + ")")
                + ". Please choose a different time.");
        this.resourceType = resourceType;
        this.resourceName = resourceName;
        this.conflictingBookingRef = conflictingBookingRef;
        this.conflictStart = conflictStart;
        this.conflictEnd = conflictEnd;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getConflictingBookingRef() {
        return conflictingBookingRef;
    }

    public LocalTime getConflictStart() {
        return conflictStart;
    }

    public LocalTime getConflictEnd() {
        return conflictEnd;
    }
}
