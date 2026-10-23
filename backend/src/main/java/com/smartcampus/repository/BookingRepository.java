package com.smartcampus.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.BookingConflictException;
import com.smartcampus.model.Booking;
import com.smartcampus.model.enums.BookingStatus;
import com.smartcampus.model.enums.ResourceType;
import com.smartcampus.utility.SqlCriteria;

/**
 * JDBC implementation of the booking queries.
 *
 * <p><b>The most important method here is {@link #findConflict(...)}.</b> It
 * returns the existing booking that overlaps a requested time window, and is
 * executed <i>inside the same transaction</i> as the INSERT - which is what
 * makes the concurrency guarantee hold at database level.</p>
 *
 * <p>Overlap predicate (half-open intervals):</p>
 * <pre>
 *   existing.start &lt; requested.end  AND  existing.end &gt; requested.start
 *
 *   [---- existing ----)
 *        [---- requested ----)   --&gt; conflict
 * </pre>
 */
@Repository
public class BookingRepository extends JdbcSupport
        implements GenericRepository<Booking, Long>, SearchableRepository<Booking> {

    private static final String BASE_SELECT = """
            SELECT id, booking_ref, resource_type, resource_id, resource_name,
                   requested_by, requested_by_name, booking_date, start_time, end_time,
                   purpose, status, created_at
            FROM bookings """;

    /** Only these two statuses still occupy a time slot. */
    private static final String BLOCKING_STATUSES = "('PENDING','APPROVED')";

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "bookingRef", "booking_ref",
            "resourceType", "resource_type",
            "resourceName", "resource_name",
            "requestedByName", "requested_by_name",
            "bookingDate", "booking_date",
            "startTime", "start_time",
            "status", "status",
            "createdAt", "created_at",
            "id", "id");

    /** Lambda row mapper. */
    private static final RowMapper<Booking> BOOKING_ROW = (rs, rowNum) -> new Booking(
            rs.getLong("id"),
            str(rs, "booking_ref"),
            enumOf(ResourceType.class, str(rs, "resource_type"), ResourceType.CLASSROOM),
            nullableLong(rs, "resource_id"),
            str(rs, "resource_name"),
            nullableLong(rs, "requested_by"),
            str(rs, "requested_by_name"),
            date(rs, "booking_date"),
            time(rs, "start_time"),
            time(rs, "end_time"),
            str(rs, "purpose"),
            enumOf(BookingStatus.class, str(rs, "status"), BookingStatus.PENDING),
            dateTime(rs, "created_at"));

    public BookingRepository(DataSource dataSource) {
        super(dataSource);
    }

    // ==================================================================
    //  GenericRepository
    // ==================================================================

    @Override
    public Booking save(Booking booking) {
        String sql = """
                INSERT INTO bookings
                    (booking_ref, resource_type, resource_id, resource_name,
                     requested_by, requested_by_name, booking_date, start_time, end_time,
                     purpose, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)""";

        long id = insert(sql, bindingValues(booking));
        booking.setId(id);
        return booking;
    }

    /**
     * Transactional variant used by the synchronised booking service: the
     * INSERT runs on the <b>same connection</b> as the conflict check, inside
     * a transaction opened by {@code TransactionExecutor}.
     */
    public Booking save(Connection connection, Booking booking) throws SQLException {
        String sql = """
                INSERT INTO bookings
                    (booking_ref, resource_type, resource_id, resource_name,
                     requested_by, requested_by_name, booking_date, start_time, end_time,
                     purpose, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)""";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            bind(statement, bindingValues(booking));

            if (statement.executeUpdate() == 0) {
                throw new SQLException("INSERT affected 0 rows");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    booking.setId(keys.getLong(1));
                }
            }
        }
        return booking;
    }

    private Object[] bindingValues(Booking booking) {
        return new Object[]{
                booking.getBookingRef(), booking.getResourceType(), booking.getResourceId(),
                booking.getResourceName(), booking.getRequestedBy(),
                booking.getRequestedByName(), booking.getBookingDate(),
                booking.getStartTime(), booking.getEndTime(), booking.getPurpose(),
                booking.getStatus()
        };
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE id = ?", BOOKING_ROW, id);
    }

    @Override
    public List<Booking> findAll() {
        return query(BASE_SELECT + " ORDER BY created_at DESC", BOOKING_ROW);
    }

    @Override
    public Booking update(Booking booking) {
        String sql = """
                UPDATE bookings SET
                    booking_ref=?, resource_type=?, resource_id=?, resource_name=?,
                    requested_by=?, requested_by_name=?, booking_date=?, start_time=?,
                    end_time=?, purpose=?, status=?
                WHERE id=?""";

        int affected = update(sql, booking.getBookingRef(), booking.getResourceType(),
                booking.getResourceId(), booking.getResourceName(), booking.getRequestedBy(),
                booking.getRequestedByName(), booking.getBookingDate(), booking.getStartTime(),
                booking.getEndTime(), booking.getPurpose(), booking.getStatus(),
                booking.getId());

        if (affected == 0) {
            throw new com.smartcampus.exception.ResourceNotFoundException(booking.getId());
        }
        return booking;
    }

    @Override
    public boolean deleteById(Long id) {
        return update("DELETE FROM bookings WHERE id = ?", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong("SELECT COUNT(*) FROM bookings");
    }

    @Override
    public boolean existsById(Long id) {
        return exists("SELECT COUNT(*) FROM bookings WHERE id = ?", id);
    }

    // ==================================================================
    //  SearchableRepository
    // ==================================================================

    @Override
    public List<Booking> search(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);

        String sql = BASE_SELECT
                + where.getWhere() + " "
                + SqlCriteria.orderBy(criteria.getSort(), SORT_COLUMNS, "created_at") + " "
                + SqlCriteria.direction(criteria.getDirection())
                + " LIMIT ?, ?";

        List<Object> params = new ArrayList<>(where.getParams());
        params.add(criteria.offset());
        params.add(criteria.getSize());

        return query(sql, BOOKING_ROW, params.toArray());
    }

    @Override
    public long count(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);
        return queryForLong("SELECT COUNT(*) FROM bookings " + where.getWhere(),
                where.getParamsArray());
    }

    private SqlCriteria buildFilter(SearchCriteria criteria) {
        SqlCriteria where = new SqlCriteria();

        if (criteria.hasKeyword()) {
            String like = "%" + criteria.getKeyword().toLowerCase() + "%";
            where.and("(LOWER(booking_ref) LIKE ? OR LOWER(resource_name) LIKE ?"
                    + " OR LOWER(requested_by_name) LIKE ? OR LOWER(purpose) LIKE ?)",
                    like, like, like, like);
        }

        where.andNotBlank(criteria.filter("resourceType"), "resource_type = ?");
        where.andNotBlank(criteria.filter("status"), "status = ?");
        where.andNotBlank(criteria.filter("resourceName"), "resource_name = ?");
        where.andNumeric(criteria.filter("requestedBy"), "requested_by = ?");
        where.andNotBlank(criteria.filter("date"), "booking_date = ?");
        where.andNotBlank(criteria.filter("fromDate"), "booking_date >= ?");
        where.andNotBlank(criteria.filter("toDate"), "booking_date <= ?");

        return where;
    }

    // ==================================================================
    //  CONFLICT DETECTION  -  the core of the booking module
    // ==================================================================

    /**
     * Finds the existing PENDING/APPROVED booking that overlaps the requested
     * window on the same resource and the same day.
     *
     * <p>Standalone version (used by the availability checker).</p>
     *
     * @param excludeId booking id to ignore (when re-saving an existing one)
     */
    public Optional<Booking> findConflict(ResourceType resourceType, Long resourceId,
                                          LocalDate date, LocalTime startTime,
                                          LocalTime endTime, Long excludeId) {
        SqlCriteria where = conflictWhere(resourceType, resourceId, date, startTime, endTime, excludeId);
        return queryOne(BASE_SELECT + where.getWhere() + " ORDER BY start_time LIMIT 1",
                BOOKING_ROW, where.getParamsArray());
    }

    /**
     * Same conflict query but running on the <b>caller's transactional
     * connection</b>, so the check and the INSERT cannot be separated by
     * another thread.
     */
    public Optional<Booking> findConflict(Connection connection,
                                          ResourceType resourceType, Long resourceId,
                                          LocalDate date, LocalTime startTime,
                                          LocalTime endTime, Long excludeId) throws SQLException {
        SqlCriteria where = conflictWhere(resourceType, resourceId, date, startTime, endTime, excludeId);
        String sql = BASE_SELECT + where.getWhere() + " ORDER BY start_time LIMIT 1";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, where.getParamsArray());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.ofNullable(BOOKING_ROW.mapRow(rs, 1)) : Optional.empty();
            }
        }
    }

    private SqlCriteria conflictWhere(ResourceType resourceType, Long resourceId,
                                      LocalDate date, LocalTime startTime,
                                      LocalTime endTime, Long excludeId) {
        SqlCriteria where = new SqlCriteria()
                .and("resource_type = ?", resourceType)
                .and("resource_id = ?", resourceId)
                .and("booking_date = ?", date)
                .and("status IN " + BLOCKING_STATUSES)
                // classic interval-overlap predicate
                .and("start_time < ?", endTime)
                .and("end_time > ?", startTime);

        if (excludeId != null) {
            where.and("id <> ?", excludeId);
        }
        return where;
    }

    /**
     * Convenience wrapper that turns a found overlap into the user-facing
     * exception, e.g.
     * <blockquote>
     *   Booking conflict: LAB-02 - Database Systems Lab is already booked
     *   from 10:00 to 12:00 (BK-0002).
     * </blockquote>
     */
    public Optional<Booking> findConflictOrThrow(ResourceType resourceType, Long resourceId,
                                                 LocalDate date, LocalTime startTime,
                                                 LocalTime endTime, Long excludeId) {
        Optional<Booking> conflict =
                findConflict(resourceType, resourceId, date, startTime, endTime, excludeId);

        conflict.ifPresent(existing -> {
            throw new BookingConflictException(resourceType, existing.getResourceName(),
                    existing.getBookingRef(), existing.getStartTime(), existing.getEndTime());
        });
        return conflict;
    }

    // ==================================================================
    //  Statistics and reporting helpers
    // ==================================================================

    /** Status histogram for the dashboard chart (all five keys are always present). */
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        // seed every status so the chart never has a missing slice
        for (BookingStatus status : BookingStatus.values()) {
            counts.put(status.name(), 0L);
        }
        query("SELECT status, COUNT(*) AS total FROM bookings GROUP BY status",
                (rs, rowNum) -> {
                    counts.put(str(rs, "status"), rs.getLong("total"));
                    return null;
                });
        return counts;
    }

    /** PENDING + APPROVED - the "Active Bookings" dashboard card. */
    public long countActive() {
        return queryForLong("SELECT COUNT(*) FROM bookings WHERE status IN " + BLOCKING_STATUSES);
    }

    public List<Booking> findByUser(Long userId, int limit) {
        return query(BASE_SELECT + " WHERE requested_by = ? ORDER BY created_at DESC LIMIT ?",
                BOOKING_ROW, userId, limit);
    }

    public List<Booking> findRecent(int limit) {
        return query(BASE_SELECT + " ORDER BY created_at DESC LIMIT ?", BOOKING_ROW, limit);
    }

    /** Change only the status (approve / reject / cancel / complete). */
    public boolean updateStatus(Long id, BookingStatus status) {
        return update("UPDATE bookings SET status = ? WHERE id = ?", status, id) > 0;
    }

    /** Highest booking id currently stored - seeds the reference counter at startup. */
    public long maxBookingId() {
        return queryForLong("SELECT COALESCE(MAX(id), 0) FROM bookings");
    }
}
