package com.smartcampus.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A course assignment.  The file uploaded by a student is stored separately in
 * {@link AssignmentSubmission}; this class only holds the instructions and
 * submission policy.
 */
public class Assignment extends BaseEntity {

    private Long courseId;
    private String courseCode;
    private String courseName;
    private String department;
    private int semester;

    private String title;
    private String instructions;
    private LocalDateTime dueAt;
    private String allowedFileTypes;
    private int maxFileSizeMb;
    private BigDecimal maxScore;
    private boolean allowLate;
    private boolean allowResubmission;
    private boolean published;

    private Long createdByFacultyId;
    private String createdByName;
    private LocalDateTime updatedAt;

    /** Filled by the service for list screens; not stored in assignments. */
    private int submissionCount;
    private int gradedCount;
    private AssignmentSubmission mySubmission;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public String getAllowedFileTypes() {
        return allowedFileTypes;
    }

    public void setAllowedFileTypes(String allowedFileTypes) {
        this.allowedFileTypes = allowedFileTypes;
    }

    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(int maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public boolean isAllowLate() {
        return allowLate;
    }

    public void setAllowLate(boolean allowLate) {
        this.allowLate = allowLate;
    }

    public boolean isAllowResubmission() {
        return allowResubmission;
    }

    public void setAllowResubmission(boolean allowResubmission) {
        this.allowResubmission = allowResubmission;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public Long getCreatedByFacultyId() {
        return createdByFacultyId;
    }

    public void setCreatedByFacultyId(Long createdByFacultyId) {
        this.createdByFacultyId = createdByFacultyId;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getSubmissionCount() {
        return submissionCount;
    }

    public void setSubmissionCount(int submissionCount) {
        this.submissionCount = submissionCount;
    }

    public int getGradedCount() {
        return gradedCount;
    }

    public void setGradedCount(int gradedCount) {
        this.gradedCount = gradedCount;
    }

    public AssignmentSubmission getMySubmission() {
        return mySubmission;
    }

    public void setMySubmission(AssignmentSubmission mySubmission) {
        this.mySubmission = mySubmission;
    }
}
