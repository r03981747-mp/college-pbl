package com.smartcampus.service;

import com.smartcampus.dto.AvailabilityRequest;
import com.smartcampus.dto.AvailabilityResponse;
import com.smartcampus.model.Resource;
import com.smartcampus.model.enums.ResourceType;

/**
 * Contract shared by the three bookable resource modules
 * (classroom, laboratory and equipment).
 *
 * <p><b>OOP concepts:</b></p>
 * <ul>
 *   <li><i>generics</i> - {@code ResourceService<Classroom}},
 *       {@code ResourceService<Laboratory>}, {@code ResourceService<Equipment>}</li>
 *   <li><i>bounded type parameter</i> - {@code <T extends Resource>} guarantees
 *       that only {@link Resource} subclasses are accepted</li>
 *   <li><i>interface inheritance</i> - reuses {@link Searchable} and
 *       {@link Manageable}</li>
 * </ul>
 *
 * @param <T> the concrete resource type
 */
public interface ResourceService<T extends Resource> extends Searchable<T>, Manageable<T> {

    /** Which of the three resource types this service manages. */
    ResourceType getResourceType();

    /** Load a resource or throw 404. */
    T requireResource(Long resourceId);

    /**
     * Load a resource that is <b>ACTIVE</b> and therefore bookable.
     * Throws 404 when it does not exist or is under maintenance / inactive.
     */
    T requireBookable(Long resourceId);

    /** Asks the booking module whether this resource is free for the window. */
    AvailabilityResponse checkAvailability(AvailabilityRequest request);
}
