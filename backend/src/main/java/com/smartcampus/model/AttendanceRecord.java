package com.smartcampus.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.smartcampus.model.enums.AttendanceStatus;

/** One student's attendance for one subject on one date. */
public class AttendanceRecord extends BaseEntity {

    private Long studentId;
    private String studentName;
    private String studentUsn;
    private String section;

    private Long courseId;
    private String courseCode;
    private String courseName;
    private int semester;

    private LocalDate attendanceDate;
    private AttendanceStatus status;
    private String remarks;

    private Long markedByFacultyId;
    private String markedByName;
    private LocalDateTime markedAt;

    private Long updatedByFacultyId;
    private String updatedByName;
    private LocalDateTime updatedAt;

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentUsn() {
        return studentUsn;
    }

    public void setStudentUsn(String studentUsn) {
        this.studentUsn = studentUsn;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

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

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) {
        this.semester = semester;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Long getMarkedByFacultyId() {
        return markedByFacultyId;
    }

    public void setMarkedByFacultyId(Long markedByFacultyId) {
        this.markedByFacultyId = markedByFacultyId;
    }

    public String getMarkedByName() {
        return markedByName;
    }

    public void setMarkedByName(String markedByName) {
        this.markedByName = markedByName;
    }

    public LocalDateTime getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(LocalDateTime markedAt) {
        this.markedAt = markedAt;
    }

    public Long getUpdatedByFacultyId() {
        return updatedByFacultyId;
    }

    public void setUpdatedByFacultyId(Long updatedByFacultyId) {
        this.updatedByFacultyId = updatedByFacultyId;
    }

    public String getUpdatedByName() {
        return updatedByName;
    }

    public void setUpdatedByName(String updatedByName) {
        this.updatedByName = updatedByName;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
