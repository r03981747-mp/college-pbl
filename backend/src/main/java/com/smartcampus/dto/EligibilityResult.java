package com.smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calculated eligibility status.  Nothing is stored in the database; the
 * result is always derived from the current published marks/attendance and the
 * administrator's configured thresholds.
 */
public record EligibilityResult(
        String status,
        String summary,
        BigDecimal attendancePercentage,
        BigDecimal marksPercentage,
        int attendanceRecords,
        int assessmentsCounted,
        BigDecimal minAttendancePercentage,
        BigDecimal minMarksPercentage,
        int minAssessmentsRequired,
        int minAttendanceRecords,
        boolean rulesConfigured,
        List<EligibilityCheck> checks) {
}
