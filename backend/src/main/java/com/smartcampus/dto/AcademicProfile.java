package com.smartcampus.dto;

import java.util.List;

/** Complete read-only academic overview for one student. */
public record AcademicProfile(
        StudentSummary student,
        List<CourseAcademic> courses,
        EligibilityResult eligibility,
        FacultySummary classTeacher,
        FacultySummary mentor) {
}
