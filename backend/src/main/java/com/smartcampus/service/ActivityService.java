package com.smartcampus.service;

import java.time.LocalDate;
import java.util.List;

import com.smartcampus.model.CampusActivity;

/**
 * Business operations of the campus activity / event module.
 */
public interface ActivityService extends Searchable<CampusActivity>, Manageable<CampusActivity> {

    /** Events that have not finished yet, soonest first (dashboard + events page). */
    List<CampusActivity> upcoming(int limit);

    /** Number of events that are still upcoming. */
    long countUpcoming();

    /** Events active on (or spanning) the given day - used by the calendar view. */
    List<CampusActivity> onDate(LocalDate day);

    /** Distinct organizer names - fills the filter drop-down. */
    List<String> organizers();
}
