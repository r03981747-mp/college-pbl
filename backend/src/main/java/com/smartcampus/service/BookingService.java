package com.smartcampus.service;

import java.util.List;

import com.smartcampus.dto.AvailabilityRequest;
import com.smartcampus.dto.AvailabilityResponse;
import com.smartcampus.model.Booking;
import com.smartcampus.model.enums.BookingStatus;

/**
 * Business contract of the resource-booking module.
 *
 * <p>Besides the standard searchable CRUD inherited from
 * {@link Searchable} / {@link Manageable}, it defines the operations that make
 * booking special: availability checking, conflict-safe creation and status
 * transitions.</p>
 *
 * <p><b>OOP concept:</b> <i>interface inheritance</i> - a contract built from
 * other contracts.</p>
 */
public interface BookingService extends Searchable<Booking>, Manageable<Booking> {

    /**
     * Creates a booking after validating the request, checking the resource
     * and rejecting overlapping bookings.
     *
     * <p>The requester is taken from the authenticated session - never from
     * the request body.</p>
     */
    Booking create(Booking request);

    /**
     * Overloaded creation used when an administrator or faculty member books
     * on behalf of somebody else and wants the booking approved immediately.
     *
     * @param autoApprove {@code true} to store the booking as APPROVED
     */
    Booking create(Booking request, boolean autoApprove);

    /** Approves / rejects / completes an existing booking. */
    Booking changeStatus(Long id, BookingStatus status);

    /** Cancels a booking (only the owner, an administrator or a faculty member may). */
    Booking cancel(Long id);

    /** Asks whether a resource is free for the requested window. */
    AvailabilityResponse checkAvailability(AvailabilityRequest request);

    /** Bookings created by the signed-in user. */
    List<Booking> myBookings();

    /**
     * Ownership-aware lookup: administrators and faculty members see
     * everything, students only their own bookings (403 otherwise).
     */
    Booking getVisible(Long id);
}
