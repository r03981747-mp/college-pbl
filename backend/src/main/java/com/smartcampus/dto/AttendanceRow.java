package com.smartcampus.dto;

import java.time.LocalDateTime;

/** One row of a faculty attendance register. */
public record AttendanceRow(
        StudentSummary student,
        String status,
        String remarks,
        Long recordId,
        String markedByName,
        LocalDateTime markedAt,
        String updatedByName,
        LocalDateTime updatedAt) {
}
