package com.smartcampus.model;

import com.smartcampus.model.enums.ResourceStatus;

/**
 * Abstract superclass of every bookable campus resource.
 *
 * <p><b>OOP concepts:</b> <i>abstraction</i> (cannot be instantiated),
 * <i>inheritance</i> ({@code Classroom}, {@code Laboratory} and
 * {@code Equipment} extend it) and <i>polymorphism</i>
 * ({@link #getResourceCode()}, {@link #getResourceLabel()} and
 * {@link #describeForBooking()} are overridden by every subclass).</p>
 *
 * <pre>
 *          Resource
 *        /    |     \
 * Classroom Laboratory Equipment
 * </pre>
 */
public abstract class Resource extends BaseEntity {

    private ResourceStatus status;

    /** Default constructor - every new resource starts its life active. */
    public Resource() {
        super();
        this.status = ResourceStatus.ACTIVE;
    }

    /** Constructor with an explicit status. */
    public Resource(Long id, ResourceStatus status) {
        super(id);
        this.status = status == null ? ResourceStatus.ACTIVE : status;
    }

    /** Short machine code of the resource, e.g. {@code C-101}, {@code LAB-02}, serial no. */
    public abstract String getResourceCode();

    /** Display label used when a booking snapshot is stored, e.g. {@code LAB-02 - Database Lab}. */
    public abstract String getResourceLabel();

    /**
     * Polymorphic description used by the booking module.  Each subclass
     * appends its own extra detail by calling {@code super.describeForBooking()}.
     */
    public String describeForBooking() {
        return getResourceLabel() + " [" + getResourceCode() + "] - " + status.getLabel();
    }

    /** Only an ACTIVE resource can be booked. */
    public boolean isBookable() {
        return status != null && status.isBookable();
    }

    public ResourceStatus getStatus() {
        return status;
    }

    public void setStatus(ResourceStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getResourceLabel() + ", " + status + "}";
    }
}
