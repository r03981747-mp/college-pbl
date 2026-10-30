package com.smartcampus.dto;

/**
 * One requirement shown in the eligibility explanation.
 *
 * @param result MET, UNMET, PENDING or NOT_REQUIRED
 */
public record EligibilityCheck(String label, String result, String detail) {
}
