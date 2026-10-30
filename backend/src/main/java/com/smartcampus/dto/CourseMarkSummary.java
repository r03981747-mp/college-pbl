package com.smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

import com.smartcampus.model.Course;
import com.smartcampus.model.StudentMark;

/** Marks for one subject, with totals only when at least one mark exists. */
public record CourseMarkSummary(
        Course course,
        List<StudentMark> marks,
        BigDecimal earned,
        BigDecimal max,
        BigDecimal percentage,
        int assessmentCount) {
}
