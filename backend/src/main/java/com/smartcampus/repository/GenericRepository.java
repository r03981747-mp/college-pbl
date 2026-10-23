package com.smartcampus.repository;

import java.util.List;
import java.util.Optional;

/**
 * Generic CRUD contract shared by every repository in the system.
 *
 * <p><b>OOP concept - generics:</b> one interface, many concrete types.
 * {@code StudentRepository implements GenericRepository<Student, Long>},
 * {@code CourseRepository implements GenericRepository<Course, Long>}, ... so
 * the CRUD signatures stay type safe without duplicating them.</p>
 *
 * @param <T>  the entity type handled by the repository
 * @param <ID> the type of the primary key ({@link Long} everywhere here)
 */
public interface GenericRepository<T, ID> {

    /** Insert a new row and write the generated primary key back into the entity. */
    T save(T entity);

    /** Load one row; empty {@link Optional} when it does not exist. */
    Optional<T> findById(ID id);

    /** Load every row (no filtering). */
    List<T> findAll();

    /** Update an existing row by primary key. */
    T update(T entity);

    /** Delete one row; {@code true} when a row was actually removed. */
    boolean deleteById(ID id);

    /** Total number of rows. */
    long count();

    /** Cheap existence probe used before an update / delete. */
    boolean existsById(ID id);
}
