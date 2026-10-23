package com.smartcampus.dto;

import java.time.LocalTime;

/**
 * Answer of the availability check. When the slot is taken, the conflicting
 * booking's time window is returned so the UI can show a precise message
 * such as:
 * <blockquote>
 * "Booking conflict: Laboratory LAB-02 is already booked from 10:00 to 12:00."
 * </blockquote>
 */
public class AvailabilityResponse {

    private boolean available;
    private String message;
    private String conflictingBookingRef;
    private LocalTime conflictStart;
    private LocalTime conflictEnd;

    public AvailabilityResponse() {
    }

    private AvailabilityResponse(boolean available, String message, String ref,
                                 LocalTime start, LocalTime end) {
        this.available = available;
        this.message = message;
        this.conflictingBookingRef = ref;
        this.conflictStart = start;
        this.conflictEnd = end;
    }

    public static AvailabilityResponse free(String message) {
        return new AvailabilityResponse(true, message, null, null, null);
    }

    public static AvailabilityResponse busy(String message, String ref,
                                            LocalTime start, LocalTime end) {
        return new AvailabilityResponse(false, message, ref, start, end);
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConflictingBookingRef() {
        return conflictingBookingRef;
    }

    public void setConflictingBookingRef(String conflictingBookingRef) {
        this.conflictingBookingRef = conflictingBookingRef;
    }

    public LocalTime getConflictStart() {
        return conflictStart;
    }

    public void setConflictStart(LocalTime conflictStart) {
        this.conflictStart = conflictStart;
    }

    public LocalTime getConflictEnd() {
        return conflictEnd;
    }

    public void setConflictEnd(LocalTime conflictEnd) {
        this.conflictEnd = conflictEnd;
    }
}
