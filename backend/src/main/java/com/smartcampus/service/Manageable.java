package com.smartcampus.service;

import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;

/**
 * Standard CRUD contract of the service layer.
 *
 * <p><b>OOP concept:</b> <i>generics</i> - one interface, implemented by
 * {@code StudentService}, {@code FacultyService}, {@code CourseService},
 * {@code ActivityService} and the three resource services, so the controllers
 * all look the same.</p>
 *
 * <p>The service layer is also where validation, duplicate detection and audit
 * logging happen - repositories only talk SQL.</p>
 *
 * @param <T> entity type managed by the service
 */
public interface Manageable<T> {

    /** One page of records, filtered / sorted according to {@code criteria}. */
    PagedResponse<T> list(SearchCriteria criteria);

    /** Load a single record or throw the module's *NotFoundException (HTTP 404). */
    T getById(Long id);

    /** Validate and persist a brand new record; returns it with its generated id. */
    T create(T entity);

    /** Validate and overwrite an existing record. */
    T update(Long id, T entity);

    /** Remove a record (throws 404 when it does not exist). */
    void delete(Long id);
}
