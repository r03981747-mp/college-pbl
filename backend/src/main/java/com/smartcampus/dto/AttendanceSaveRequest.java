package com.smartcampus.dto;

import java.time.LocalDate;
import java.util.List;

/** Bulk attendance save/correction payload. */
public record AttendanceSaveRequest(
        Long courseId,
        LocalDate date,
        List<AttendanceEntry> records) {
}
