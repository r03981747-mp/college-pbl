package com.smartcampus.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.smartcampus.model.enums.BookingStatus;
import com.smartcampus.model.enums.ResourceType;

/**
 * A request to use a campus resource (classroom, laboratory or equipment)
 * during a specific time window.
 *
 * <p><b>OOP concepts:</b> <i>constructor overloading</i> - three constructors
 * of increasing completeness, including a copy constructor used by the
 * concurrency test.</p>
 */
public class Booking extends BaseEntity {

    private String bookingRef;
    private ResourceType resourceType;
    private Long resourceId;
    private String resourceName;
    private Long requestedBy;
    private String requestedByName;
    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String purpose;
    private BookingStatus status;

    /** No-argument constructor (Jackson / JDBC row mapping). */
    public Booking() {
        super();
        this.status = BookingStatus.PENDING;
    }

    /**
     * Copy constructor - creates a brand new independent {@link Booking}
     * object from an existing one (a new object, not a shared reference).
     */
    public Booking(Booking other) {
        this();
        if (other != null) {
            this.bookingRef = other.bookingRef;
            this.resourceType = other.resourceType;
            this.resourceId = other.resourceId;
            this.resourceName = other.resourceName;
            this.requestedBy = other.requestedBy;
            this.requestedByName = other.requestedByName;
            this.bookingDate = other.bookingDate;
            this.startTime = other.startTime;
            this.endTime = other.endTime;
            this.purpose = other.purpose;
            this.status = other.status;
        }
    }

    /** Full constructor. */
    public Booking(Long id, String bookingRef, ResourceType resourceType, Long resourceId,
                   String resourceName, Long requestedBy, String requestedByName,
                   LocalDate bookingDate, LocalTime startTime, LocalTime endTime,
                   String purpose, BookingStatus status, LocalDateTime createdAt) {
        super(id, createdAt);
        this.bookingRef = bookingRef;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.requestedBy = requestedBy;
        this.requestedByName = requestedByName;
        this.bookingDate = bookingDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.purpose = purpose;
        this.status = status;
    }

    /**
     * Detects whether this booking overlaps another one on the same resource
     * and the same day.
     *
     * <pre>
     *   [---- this ----)
     *        [---- other ----)   -> overlap
     * </pre>
     *
     * @param other the candidate booking (same resource, same date)
     * @return {@code true} when the two time windows intersect
     */
    public boolean overlaps(Booking other) {
        if (other == null || bookingDate == null || startTime == null || endTime == null) {
            return false;
        }
        if (!bookingDate.equals(other.bookingDate)) {
            return false;
        }
        if (!resourceType.equals(other.resourceType) || !resourceId.equals(other.resourceId)) {
            return false;
        }
        // half-open intervals: [start, end)
        return startTime.isBefore(other.getEndTime()) && endTime.isAfter(other.getStartTime());
    }

    /** True while the booking still occupies the slot. */
    public boolean isActive() {
        return status != null && status.blocksSlot();
    }

    public String getBookingRef() {
        return bookingRef;
    }

    public void setBookingRef(String bookingRef) {
        this.bookingRef = bookingRef;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Long requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getRequestedByName() {
        return requestedByName;
    }

    public void setRequestedByName(String requestedByName) {
        this.requestedByName = requestedByName;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Booking{" + bookingRef + " " + resourceType + "#" + resourceId
                + " " + bookingDate + " " + startTime + "-" + endTime + " " + status + "}";
    }
}
