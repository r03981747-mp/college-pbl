package com.smartcampus.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Single-row configuration for eligibility.  The defaults are deliberately
 * marked as unconfigured until an administrator explicitly saves the rules.
 */
public class AcademicSettings extends BaseEntity {

    private BigDecimal minAttendancePercentage = new BigDecimal("75.00");
    private BigDecimal minMarksPercentage = new BigDecimal("40.00");
    private int minAssessmentsRequired = 1;
    private int minAttendanceRecords = 1;
    private boolean requireMarks = true;
    private boolean requireAttendance = true;
    private boolean lateCountsAsPresent = true;
    private boolean rulesConfigured;
    private Long updatedByUserId;
    private String updatedByName;
    private LocalDateTime updatedAt;

    public BigDecimal getMinAttendancePercentage() {
        return minAttendancePercentage;
    }

    public void setMinAttendancePercentage(BigDecimal minAttendancePercentage) {
        this.minAttendancePercentage = minAttendancePercentage;
    }

    public BigDecimal getMinMarksPercentage() {
        return minMarksPercentage;
    }

    public void setMinMarksPercentage(BigDecimal minMarksPercentage) {
        this.minMarksPercentage = minMarksPercentage;
    }

    public int getMinAssessmentsRequired() {
        return minAssessmentsRequired;
    }

    public void setMinAssessmentsRequired(int minAssessmentsRequired) {
        this.minAssessmentsRequired = minAssessmentsRequired;
    }

    public int getMinAttendanceRecords() {
        return minAttendanceRecords;
    }

    public void setMinAttendanceRecords(int minAttendanceRecords) {
        this.minAttendanceRecords = minAttendanceRecords;
    }

    public boolean isRequireMarks() {
        return requireMarks;
    }

    public void setRequireMarks(boolean requireMarks) {
        this.requireMarks = requireMarks;
    }

    public boolean isRequireAttendance() {
        return requireAttendance;
    }

    public void setRequireAttendance(boolean requireAttendance) {
        this.requireAttendance = requireAttendance;
    }

    public boolean isLateCountsAsPresent() {
        return lateCountsAsPresent;
    }

    public void setLateCountsAsPresent(boolean lateCountsAsPresent) {
        this.lateCountsAsPresent = lateCountsAsPresent;
    }

    public boolean isRulesConfigured() {
        return rulesConfigured;
    }

    public void setRulesConfigured(boolean rulesConfigured) {
        this.rulesConfigured = rulesConfigured;
    }

    public Long getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(Long updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
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
