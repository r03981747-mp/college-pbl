package com.smartcampus.model;

import java.time.LocalDate;
import java.time.LocalTime;

import com.smartcampus.model.enums.ActivityCategory;
import com.smartcampus.model.enums.ActivityStatus;

/**
 * A campus activity / event (seminar, workshop, cultural night, tournament...).
 */
public class CampusActivity extends BaseEntity {

    private String activityName;
    private String description;
    private ActivityCategory category;
    private String organizer;
    private String venue;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private int maxParticipants;
    private ActivityStatus status;

    /** No-argument constructor (Jackson / JDBC row mapping). */
    public CampusActivity() {
        super();
        this.status = ActivityStatus.SCHEDULED;
        this.category = ActivityCategory.OTHER;
    }

    /** Partial constructor - name and venue only. */
    public CampusActivity(Long id, String activityName, String venue) {
        super(id);
        this.activityName = activityName;
        this.venue = venue;
        this.status = ActivityStatus.SCHEDULED;
        this.category = ActivityCategory.OTHER;
    }

    /** Full constructor. */
    public CampusActivity(Long id, String activityName, String description,
                          ActivityCategory category, String organizer, String venue,
                          LocalDate startDate, LocalDate endDate, LocalTime startTime,
                          LocalTime endTime, int maxParticipants, ActivityStatus status) {
        super(id);
        this.activityName = activityName;
        this.description = description;
        this.category = category;
        this.organizer = organizer;
        this.venue = venue;
        this.startDate = startDate;
        this.endDate = endDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxParticipants = maxParticipants;
        this.status = status;
    }

    /** {@code true} when the event has not finished yet (used by the dashboard). */
    public boolean isUpcoming(LocalDate today) {
        return endDate != null
                && !endDate.isBefore(today)
                && (status == ActivityStatus.SCHEDULED || status == ActivityStatus.ONGOING);
    }

    /** Duration of the event in whole days (at least 1). */
    public long getDurationInDays() {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return 1;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ActivityCategory getCategory() {
        return category;
    }

    public void setCategory(ActivityCategory category) {
        this.category = category;
    }

    public String getOrganizer() {
        return organizer;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
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

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public void setMaxParticipants(int maxParticipants) {
        this.maxParticipants = maxParticipants;
    }

    public ActivityStatus getStatus() {
        return status;
    }

    public void setStatus(ActivityStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "CampusActivity{" + activityName + " @ " + venue + "}";
    }
}
