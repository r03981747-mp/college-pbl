package com.smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

import com.smartcampus.model.StudentMark;

/** Student-facing marks payload grouped by subject. */
public record MyMarks(
        List<StudentMark> marks,
        List<CourseMarkSummary> courses,
        BigDecimal totalEarned,
        BigDecimal totalMax,
        BigDecimal overallPercentage,
        int assessmentCount) {
}
