package com.smartcampus.dto;

/** A student row plus the current eligibility result. */
public record AcademicStudentSummary(StudentSummary student, EligibilityResult eligibility) {
}
