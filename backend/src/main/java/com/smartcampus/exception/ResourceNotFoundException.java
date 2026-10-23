package com.smartcampus.exception;

import com.smartcampus.model.enums.ResourceType;

/**
 * Thrown when a classroom / laboratory / equipment record does not exist,
 * or when the record exists but is not in ACTIVE status (i.e. cannot be
 * booked).  Maps to HTTP 404.
 */
public class ResourceNotFoundException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(Long id) {
        super("Resource not found with id " + id);
    }

    /** Overloaded constructor - names the resource type in the message. */
    public ResourceNotFoundException(ResourceType type, Long id) {
        super(type.getLabel() + " not found with id " + id);
    }

    /** Overloaded constructor - lookup by human readable code such as {@code LAB-02}. */
    public ResourceNotFoundException(String code) {
        super("Resource not found with code " + code);
    }

    /**
     * Used when the resource row exists but is under maintenance / inactive,
     * so it must not accept new bookings.
     */
    public static ResourceNotFoundException notBookable(String label, String status) {
        return new ResourceNotFoundException(label + " cannot be booked (current status: " + status + ")");
    }
}
