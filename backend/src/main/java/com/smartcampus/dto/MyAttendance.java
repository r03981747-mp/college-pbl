package com.smartcampus.dto;

import java.util.List;

/** Student-facing attendance payload grouped by subject. */
public record MyAttendance(
        StudentSummary student,
        List<CourseAttendance> courses,
        AttendanceSummary overall) {
}
