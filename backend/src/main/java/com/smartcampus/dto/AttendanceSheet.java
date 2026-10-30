package com.smartcampus.dto;

import java.time.LocalDate;
import java.util.List;

import com.smartcampus.model.Course;

/** A course/date attendance register. */
public record AttendanceSheet(Course course, LocalDate date, List<AttendanceRow> rows) {
}
