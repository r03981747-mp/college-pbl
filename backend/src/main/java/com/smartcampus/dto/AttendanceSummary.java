package com.smartcampus.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.smartcampus.model.AttendanceRecord;
import com.smartcampus.model.enums.AttendanceStatus;

/** Counts and percentage derived from attendance rows. */
public record AttendanceSummary(
        int present,
        int absent,
        int late,
        int total,
        BigDecimal percentage) {

    public static AttendanceSummary from(List<AttendanceRecord> records, boolean lateCountsAsPresent) {
        int present = 0;
        int absent = 0;
        int late = 0;
        for (AttendanceRecord record : records) {
            if (record.getStatus() == AttendanceStatus.PRESENT) {
                present++;
            } else if (record.getStatus() == AttendanceStatus.ABSENT) {
                absent++;
            } else if (record.getStatus() == AttendanceStatus.LATE) {
                late++;
            }
        }
        int total = records.size();
        int attended = present + (lateCountsAsPresent ? late : 0);
        BigDecimal percentage = total == 0
                ? null
                : BigDecimal.valueOf(attended * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
        return new AttendanceSummary(present, absent, late, total, percentage);
    }

    public static AttendanceSummary empty(boolean lateCountsAsPresent) {
        return from(List.of(), lateCountsAsPresent);
    }
}
