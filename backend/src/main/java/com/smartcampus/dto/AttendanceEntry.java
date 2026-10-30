package com.smartcampus.dto;

/** One student's attendance choice in a save request. */
public record AttendanceEntry(Long studentId, String status, String remarks) {
}
