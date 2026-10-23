package com.smartcampus.service;

import java.util.List;

import com.smartcampus.model.Student;

/**
 * Business operations of the student module.
 *
 * <p>Extends both generic contracts, so the class is automatically
 * {@code Searchable} (keyword search) and {@code Manageable} (paged CRUD).</p>
 */
public interface StudentService extends Searchable<Student>, Manageable<Student> {

    /** Looks a student up by USN; throws 404 when it does not exist. */
    Student getByUsn(String usn);

    /** Distinct department names - used to fill the filter drop-down. */
    List<String> departments();

    /**
     * The student record belonging to the signed-in account.
     * A student only ever sees <b>his or her own</b> profile.
     */
    Student getOwnProfile();

    /**
     * Ownership guard used by the controllers: an administrator or faculty
     * member may open any record, a student only the one linked to the
     * logged-in account.
     *
     * @return the record when access is allowed (throws 403 otherwise)
     */
    Student getVisible(Long studentId);
}
