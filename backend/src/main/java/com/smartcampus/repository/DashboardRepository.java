package com.smartcampus.repository;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.model.enums.BookingStatus;
import com.smartcampus.model.enums.ResourceStatus;
import com.smartcampus.utility.SqlCriteria;

/**
 * All aggregate queries behind the dashboard cards and charts.
 *
 * <p>Nothing on the dashboard is hardcoded - every number is a live
 * {@code SELECT COUNT(*)} / {@code GROUP BY} against MySQL.</p>
 */
@Repository
public class DashboardRepository extends JdbcSupport {

    public DashboardRepository(DataSource dataSource) {
        super(dataSource);
    }

    // ==================================================================
    //  Headline cards
    // ==================================================================

    public long countStudents() {
        return queryForLong("SELECT COUNT(*) FROM students");
    }

    public long countFaculty() {
        return queryForLong("SELECT COUNT(*) FROM faculty");
    }

    public long countCourses() {
        return queryForLong("SELECT COUNT(*) FROM courses");
    }

    public long countClassrooms() {
        return queryForLong("SELECT COUNT(*) FROM classrooms");
    }

    public long countLaboratories() {
        return queryForLong("SELECT COUNT(*) FROM laboratories");
    }

    public long countEquipment() {
        return queryForLong("SELECT COUNT(*) FROM equipment");
    }

    /** Events that have not finished yet (SCHEDULED or ONGOING). */
    public long countUpcomingActivities() {
        return queryForLong("SELECT COUNT(*) FROM activities "
                + "WHERE end_date >= CURRENT_DATE AND status IN ('SCHEDULED','ONGOING')");
    }

    /** PENDING + APPROVED bookings. */
    public long countActiveBookings() {
        return queryForLong("SELECT COUNT(*) FROM bookings WHERE status IN ('PENDING','APPROVED')");
    }

    // ==================================================================
    //  Charts
    // ==================================================================

    /**
     * Student distribution per department.
     *
     * @return ordered map such as {@code {Computer Science=5, ...}}
     */
    public Map<String, Long> studentsByDepartment() {
        Map<String, Long> result = new LinkedHashMap<>();
        query("SELECT department, COUNT(*) AS total FROM students "
                        + "GROUP BY department ORDER BY department",
                (rs, rowNum) -> {
                    result.put(str(rs, "department"), rs.getLong("total"));
                    return null;
                });
        return result;
    }

    /** Student distribution per semester (semesters 1..8 in order). */
    public Map<String, Long> studentsBySemester() {
        Map<String, Long> result = new LinkedHashMap<>();
        // seed all eight semesters so the chart axis is never incomplete
        for (int semester = 1; semester <= 8; semester++) {
            result.put("Semester " + semester, 0L);
        }
        query("SELECT semester, COUNT(*) AS total FROM students GROUP BY semester ORDER BY semester",
                (rs, rowNum) -> {
                    result.put("Semester " + rs.getInt("semester"), rs.getLong("total"));
                    return null;
                });
        return result;
    }

    /** Booking histogram with all five statuses always present. */
    public Map<String, Long> bookingsByStatus() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) {
            result.put(status.getLabel(), 0L);
        }
        query("SELECT status, COUNT(*) AS total FROM bookings GROUP BY status",
                (rs, rowNum) -> {
                    String name = str(rs, "status");
                    BookingStatus status = enumOf(BookingStatus.class, name, null);
                    if (status != null) {
                        result.put(status.getLabel(), rs.getLong("total"));
                    }
                    return null;
                });
        return result;
    }

    // ==================================================================
    //  Resource utilisation per module
    // ==================================================================

    public Map<String, Long> classroomStatusCounts() {
        return statusCounts("classrooms");
    }

    public Map<String, Long> laboratoryStatusCounts() {
        return statusCounts("laboratories");
    }

    public Map<String, Long> equipmentStatusCounts() {
        return statusCounts("equipment");
    }

    /**
     * Total / active / maintenance / inactive for one resource table.
     *
     * <p><b>Why is the table name concatenated here?</b> MySQL cannot bind an
     * <i>identifier</i> as a PreparedStatement parameter, and every caller
     * passes an internal literal ({@code "classrooms"},
     * {@code "laboratories"}, {@code "equipment"}) - never user input.  All
     * <i>values</i> elsewhere in this class are still bound.</p>
     */
    private Map<String, Long> statusCounts(String table) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", queryForLong("SELECT COUNT(*) FROM " + table));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        query("SELECT status, COUNT(*) AS total FROM " + table + " GROUP BY status",
                (rs, rowNum) -> {
                    byStatus.put(str(rs, "status"), rs.getLong("total"));
                    return null;
                });

        for (ResourceStatus status : ResourceStatus.values()) {
            counts.put(status.name().toLowerCase(), byStatus.getOrDefault(status.name(), 0L));
        }
        return counts;
    }

    /** Departments that actually have students (used as chart labels). */
    public long countDistinctDepartments() {
        return queryForLong("SELECT COUNT(DISTINCT department) FROM students");
    }

    /** Small helper so the service can reuse the same safe ORDER BY rules. */
    public static String direction(String requested) {
        return SqlCriteria.direction(requested);
    }
}
