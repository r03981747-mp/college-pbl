package com.smartcampus.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.model.AcademicSettings;
import com.smartcampus.repository.AcademicSettingsRepository;
import com.smartcampus.security.AuthContext;

/** Eligibility rule configuration. */
@Service
public class AcademicSettingsService {

    private final AcademicSettingsRepository repository;
    private final AcademicAccessService access;
    private final AuditLogService auditLog;

    public AcademicSettingsService(AcademicSettingsRepository repository,
                                   AcademicAccessService access,
                                   AuditLogService auditLog) {
        this.repository = repository;
        this.access = access;
        this.auditLog = auditLog;
    }

    public AcademicSettings get() {
        return repository.get().orElseGet(() -> {
            AcademicSettings defaults = new AcademicSettings();
            defaults.setId(1L);
            defaults.setRulesConfigured(false);
            return defaults;
        });
    }

    public AcademicSettings update(AcademicSettings input) {
        access.currentUser();
        if (!access.isAdmin()) {
            throw new com.smartcampus.exception.ForbiddenException(
                    "Only an administrator can change eligibility rules.");
        }

        if (input.getMinAttendancePercentage() == null
                || input.getMinMarksPercentage() == null) {
            throw new InvalidInputException("Both percentage thresholds are required.");
        }
        validatePercentage(input.getMinAttendancePercentage(), "Minimum attendance percentage");
        validatePercentage(input.getMinMarksPercentage(), "Minimum marks percentage");

        if (input.getMinAssessmentsRequired() < 0 || input.getMinAssessmentsRequired() > 100) {
            throw new InvalidInputException("Minimum assessments must be between 0 and 100.");
        }
        if (input.getMinAttendanceRecords() < 0 || input.getMinAttendanceRecords() > 500) {
            throw new InvalidInputException("Minimum attendance records must be between 0 and 500.");
        }
        if (!input.isRequireMarks() && !input.isRequireAttendance()) {
            throw new InvalidInputException(
                    "Enable at least one requirement (marks or attendance).");
        }

        input.setId(1L);
        input.setRulesConfigured(true);
        AcademicSettings saved = repository.save(input, AuthContext.userId());
        auditLog.record("UPDATE", "ACADEMIC_SETTINGS",
                "Updated eligibility rules (attendance "
                        + saved.getMinAttendancePercentage() + "%, marks "
                        + saved.getMinMarksPercentage() + "%).");
        return saved;
    }

    private void validatePercentage(BigDecimal value, String label) {
        if (value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(new BigDecimal("100")) > 0) {
            throw new InvalidInputException(label + " must be between 0 and 100.");
        }
    }
}
