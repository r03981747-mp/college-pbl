package com.smartcampus.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.DashboardStatistics;
import com.smartcampus.dto.DashboardStatistics.ResourceSummary;
import com.smartcampus.model.Booking;
import com.smartcampus.model.CampusActivity;
import com.smartcampus.repository.ActivityRepository;
import com.smartcampus.repository.BookingRepository;
import com.smartcampus.repository.DashboardRepository;

/**
 * Assembles every number and chart shown on the dashboard.
 *
 * <p>All values come from MySQL through {@link DashboardRepository} - the
 * front end never receives a hardcoded statistic.</p>
 *
 * <p><b>Syllabus concepts used here:</b></p>
 * <ul>
 *   <li><b>Lambda + {@code Function}</b> - converting raw counters into the
 *       chart model</li>
 *   <li><b>Lambda + {@code Predicate}</b> - selecting the events worth
 *       highlighting</li>
 *   <li><b>Stream API</b> - {@code filter}, {@code map}, {@code limit},
 *       {@code collect}</li>
 *   <li><b>Collections</b> - {@code LinkedHashMap} keeps the chart order
 *       stable</li>
 * </ul>
 */
@Service
public class DashboardService {

    private static final int UPCOMING_HIGHLIGHTS = 6;
    private static final int RECENT_BOOKINGS = 6;
    private static final int HORIZON_DAYS = 30;

    private final DashboardRepository dashboardRepository;
    private final ActivityRepository activityRepository;
    private final BookingRepository bookingRepository;

    public DashboardService(DashboardRepository dashboardRepository,
                            ActivityRepository activityRepository,
                            BookingRepository bookingRepository) {
        this.dashboardRepository = dashboardRepository;
        this.activityRepository = activityRepository;
        this.bookingRepository = bookingRepository;
    }

    /** Everything the dashboard page needs, in one round trip. */
    public DashboardStatistics statistics() {
        DashboardStatistics stats = new DashboardStatistics();

        // ---- headline cards (SELECT COUNT(*) ...) ---------------------
        stats.setTotalStudents(dashboardRepository.countStudents());
        stats.setTotalFaculty(dashboardRepository.countFaculty());
        stats.setTotalCourses(dashboardRepository.countCourses());
        stats.setTotalClassrooms(dashboardRepository.countClassrooms());
        stats.setTotalLaboratories(dashboardRepository.countLaboratories());
        stats.setTotalEquipment(dashboardRepository.countEquipment());
        stats.setUpcomingActivities(dashboardRepository.countUpcomingActivities());
        stats.setActiveBookings(dashboardRepository.countActiveBookings());

        // ---- charts ---------------------------------------------------
        stats.setStudentsByDepartment(dashboardRepository.studentsByDepartment());
        stats.setStudentsBySemester(dashboardRepository.studentsBySemester());
        stats.setBookingsByStatus(dashboardRepository.bookingsByStatus());

        // ---- resource utilisation ------------------------------------
        stats.setResourceUtilization(resourceUtilization());

        // ---- upcoming events (stream + predicates) -------------------
        stats.setUpcomingEvents(upcomingHighlights());

        // ---- recent bookings (stream + map + collect) ----------------
        stats.setRecentBookings(recentBookingRows());

        return stats;
    }

    // ==================================================================
    //  Resource utilisation chart
    // ==================================================================

    /**
     * A single {@code Function} lambda converts each raw counter map into the
     * chart model, and is reused for all three modules - the classic
     * "write once, apply many" use of {@code java.util.function.Function}.
     */
    private Map<String, ResourceSummary> resourceUtilization() {
        Function<Map<String, Long>, ResourceSummary> summarise = counts -> new ResourceSummary(
                counts.getOrDefault("total", 0L),
                counts.getOrDefault("active", 0L),
                counts.getOrDefault("maintenance", 0L),
                counts.getOrDefault("inactive", 0L));

        Map<String, ResourceSummary> utilisation = new LinkedHashMap<>();
        utilisation.put("classrooms", summarise.apply(dashboardRepository.classroomStatusCounts()));
        utilisation.put("laboratories", summarise.apply(dashboardRepository.laboratoryStatusCounts()));
        utilisation.put("equipment", summarise.apply(dashboardRepository.equipmentStatusCounts()));
        return utilisation;
    }

    // ==================================================================
    //  Upcoming events
    // ==================================================================

    /**
     * Events worth putting on the dashboard: still running/scheduled, inside
     * the next 30 days, at most six of them.
     */
    private List<CampusActivity> upcomingHighlights() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(HORIZON_DAYS);

        Predicate<CampusActivity> startedBeforeHorizon = event ->
                event.getStartDate() != null && !event.getStartDate().isAfter(horizon);
        Predicate<CampusActivity> notCancelled = event ->
                event.getStatus() != com.smartcampus.model.enums.ActivityStatus.CANCELLED;

        return activityRepository.findUpcoming(50).stream()
                .filter(notCancelled)
                .filter(startedBeforeHorizon)
                .limit(UPCOMING_HIGHLIGHTS)
                .collect(Collectors.toList());
    }

    // ==================================================================
    //  Recent bookings table
    // ==================================================================

    /** Turns the last few bookings into JSON-friendly rows for the widget. */
    private List<Map<String, Object>> recentBookingRows() {
        return bookingRepository.findRecent(RECENT_BOOKINGS).stream()
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toRow(Booking booking) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", booking.getId());
        row.put("bookingRef", booking.getBookingRef());
        row.put("resourceName", booking.getResourceName());
        row.put("resourceType", booking.getResourceType().getLabel());
        row.put("requestedByName", booking.getRequestedByName());
        row.put("bookingDate", booking.getBookingDate());
        row.put("startTime", booking.getStartTime());
        row.put("endTime", booking.getEndTime());
        row.put("status", booking.getStatus().name());
        row.put("statusLabel", booking.getStatus().getLabel());
        return row;
    }
}
