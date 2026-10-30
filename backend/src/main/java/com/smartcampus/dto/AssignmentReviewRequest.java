package com.smartcampus.dto;

import java.math.BigDecimal;

/** Faculty grade/feedback for one submission. */
public record AssignmentReviewRequest(BigDecimal grade, String feedback) {
}
