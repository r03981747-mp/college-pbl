package com.smartcampus.dto;

import java.math.BigDecimal;

/** One student's score in a marks save request. */
public record MarkEntry(Long studentId, BigDecimal score, String remarks) {
}
