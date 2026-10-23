package com.smartcampus.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.smartcampus.model.enums.ResourceType;

/**
 * Input of the live concurrency demonstration.
 *
 * <p>The endpoint spins up {@code attempts} real Java threads that all try to
 * book the exact same resource for the exact same time window.  Because the
 * booking service is synchronised, only ONE of them may succeed - the rest
 * receive a {@code BookingConflictException}.</p>
 */
public class ConcurrencyTestRequest {

    private ResourceType resourceType = ResourceType.LABORATORY;
    private Long resourceId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private int attempts = 5;
    private String purpose = "Concurrency demonstration";

    public ConcurrencyTestRequest() {
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
