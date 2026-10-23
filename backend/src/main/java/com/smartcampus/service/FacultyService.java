package com.smartcampus.service;

import java.util.List;

import com.smartcampus.model.Faculty;

/**
 * Business operations of the faculty module
 * (searchable + full CRUD through the two generic contracts).
 */
public interface FacultyService extends Searchable<Faculty>, Manageable<Faculty> {

    /** Looks a faculty member up by employee id; throws 404 when unknown. */
    Faculty getByEmployeeId(String employeeId);

    /** Distinct department names - fills the filter drop-down. */
    List<String> departments();

    /** Distinct designations - fills the filter drop-down. */
    List<String> designations();

    /** Faculty record linked to the signed-in account (own profile). */
    Faculty getOwnProfile();
}
