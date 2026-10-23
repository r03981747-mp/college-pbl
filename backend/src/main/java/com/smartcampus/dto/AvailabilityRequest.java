package com.smartcampus.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.smartcampus.model.enums.ResourceType;

/**
 * Question asked by the UI before a booking is submitted:
 * "is this resource free on that date between these times?"
 */
public class AvailabilityRequest {

    private ResourceType resourceType;
    private Long resourceId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;

    public AvailabilityRequest() {
    }

    public AvailabilityRequest(ResourceType resourceType, Long resourceId,
                               LocalDate date, LocalTime startTime, LocalTime endTime) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
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
}
