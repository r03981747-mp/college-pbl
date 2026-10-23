package com.smartcampus.model;

import com.smartcampus.model.enums.CourseStatus;

/**
 * An academic course offered by a department.
 *
 * <p>{@code facultyName} is not stored in the courses table - it is filled in
 * by a LEFT JOIN against the faculty table so the UI can show the assigned
 * teacher without a second request.</p>
 */
public class Course extends BaseEntity {

    private String courseCode;
    private String courseName;
    private String department;
    private int semester;
    private int credits;
    private Long facultyId;
    private String facultyName;
    private String description;
    private CourseStatus status;

    /** No-argument constructor (Jackson / JDBC row mapping). */
    public Course() {
        super();
        this.status = CourseStatus.ACTIVE;
    }

    /** Partial constructor - code and title only. */
    public Course(Long id, String courseCode, String courseName) {
        super(id);
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.status = CourseStatus.ACTIVE;
    }

    /** Full constructor. */
    public Course(Long id, String courseCode, String courseName, String department,
                  int semester, int credits, Long facultyId, String facultyName,
                  String description, CourseStatus status) {
        super(id);
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.department = department;
        this.semester = semester;
        this.credits = credits;
        this.facultyId = facultyId;
        this.facultyName = facultyName;
        this.description = description;
        this.status = status;
    }

    /** {@code true} when a faculty member has already been assigned. */
    public boolean hasFaculty() {
        return facultyId != null;
    }

    /** Weighted load of the course (credits scaled by semester) - used in analytics. */
    public int getCreditWeight() {
        return credits * semester;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) {
        this.semester = semester;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }

    public Long getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(Long facultyId) {
        this.facultyId = facultyId;
    }

    public String getFacultyName() {
        return facultyName;
    }

    public void setFacultyName(String facultyName) {
        this.facultyName = facultyName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public void setStatus(CourseStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Course{" + courseCode + ", " + courseName + "}";
    }
}
