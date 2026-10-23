package com.smartcampus.service;

import java.util.List;

import com.smartcampus.model.Course;

/**
 * Business operations of the course module, including faculty assignment.
 */
public interface CourseService extends Searchable<Course>, Manageable<Course> {

    /** Looks a course up by its code; throws 404 when unknown. */
    Course getByCode(String courseCode);

    /** Distinct department names - fills the filter drop-down. */
    List<String> departments();

    /**
     * Assigns (or with {@code null}, removes) the teacher of a course.
     *
     * @return the updated course
     */
    Course assignFaculty(Long courseId, Long facultyId);

    /** Courses that still have no teacher assigned. */
    List<Course> findUnassigned();
}
