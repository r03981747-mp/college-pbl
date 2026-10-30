package com.smartcampus.dto;

import java.util.List;

import com.smartcampus.model.AttendanceRecord;
import com.smartcampus.model.Course;

/** Attendance for one subject. */
public record CourseAttendance(
        Course course,
        AttendanceSummary summary,
        List<AttendanceRecord> records) {
}
