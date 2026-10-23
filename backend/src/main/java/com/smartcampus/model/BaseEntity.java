package com.smartcampus.model;

import java.time.LocalDateTime;

/**
 * Base class of every domain object in the system.
 *
 * <p><b>OOP concepts:</b> <i>class</i>, <i>inheritance</i>,
 * <i>encapsulation</i> (private fields + getters/setters) and
 * <i>constructor overloading</i>.</p>
 *
 * @author Smart Campus PBL
 */
public abstract class BaseEntity {

    private Long id;
    private LocalDateTime createdAt;

    /** Default constructor (used by JDBC row mapping and Jackson). */
    public BaseEntity() {
    }

    /** Convenience constructor when only the primary key is known. */
    public BaseEntity(Long id) {
        this.id = id;
    }

    /** Full constructor. */
    public BaseEntity(Long id, LocalDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
