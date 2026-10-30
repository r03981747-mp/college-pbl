package com.smartcampus.dto;

import java.util.List;

import com.smartcampus.model.Assignment;
import com.smartcampus.model.AttendanceRecord;
import com.smartcampus.model.Course;
import com.smartcampus.model.StudentMark;

/** One subject inside an academic profile. */
public record CourseAcademic(
        Course course,
        List<StudentMark> marks,
        AttendanceSummary attendanceSummary,
        List<AttendanceRecord> attendance,
        List<Assignment> assignments) {
}
