package com.smartcampus.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.ActivityNotFoundException;
import com.smartcampus.model.CampusActivity;
import com.smartcampus.model.enums.ActivityCategory;
import com.smartcampus.model.enums.ActivityStatus;
import com.smartcampus.utility.SqlCriteria;

/** JDBC implementation of every campus activity query (PreparedStatement only). */
@Repository
public class ActivityRepository extends JdbcSupport
        implements GenericRepository<CampusActivity, Long>, SearchableRepository<CampusActivity> {

    private static final String BASE_SELECT = """
            SELECT id, activity_name, description, category, organizer, venue,
                   start_date, end_date, start_time, end_time, max_participants,
                   status, created_at
            FROM activities """;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "activityName", "activity_name",
            "category", "category",
            "organizer", "organizer",
            "venue", "venue",
            "startDate", "start_date",
            "endDate", "end_date",
            "status", "status",
            "id", "id");

    /** Lambda row mapper. */
    private static final RowMapper<CampusActivity> ACTIVITY_ROW = (rs, rowNum) -> {
        CampusActivity activity = new CampusActivity(
                rs.getLong("id"),
                str(rs, "activity_name"),
                str(rs, "venue"));
        activity.setDescription(str(rs, "description"));
        activity.setCategory(enumOf(ActivityCategory.class,
                str(rs, "category"), ActivityCategory.OTHER));
        activity.setOrganizer(str(rs, "organizer"));
        activity.setStartDate(date(rs, "start_date"));
        activity.setEndDate(date(rs, "end_date"));
        activity.setStartTime(time(rs, "start_time"));
        activity.setEndTime(time(rs, "end_time"));
        activity.setMaxParticipants(intOf(rs, "max_participants"));
        activity.setStatus(enumOf(ActivityStatus.class,
                str(rs, "status"), ActivityStatus.SCHEDULED));
        activity.setCreatedAt(dateTime(rs, "created_at"));
        return activity;
    };

    public ActivityRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public CampusActivity save(CampusActivity activity) {
        String sql = """
                INSERT INTO activities
                    (activity_name, description, category, organizer, venue,
                     start_date, end_date, start_time, end_time, max_participants, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)""";

        long id = insert(sql,
                activity.getActivityName(), activity.getDescription(), activity.getCategory(),
                activity.getOrganizer(), activity.getVenue(), activity.getStartDate(),
                activity.getEndDate(), activity.getStartTime(), activity.getEndTime(),
                activity.getMaxParticipants(), activity.getStatus());

        activity.setId(id);
        return activity;
    }

    @Override
    public Optional<CampusActivity> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE id = ?", ACTIVITY_ROW, id);
    }

    @Override
    public List<CampusActivity> findAll() {
        return query(BASE_SELECT + " ORDER BY start_date DESC", ACTIVITY_ROW);
    }

    @Override
    public CampusActivity update(CampusActivity activity) {
        String sql = """
                UPDATE activities SET
                    activity_name=?, description=?, category=?, organizer=?, venue=?,
                    start_date=?, end_date=?, start_time=?, end_time=?,
                    max_participants=?, status=?
                WHERE id=?""";

        int affected = update(sql,
                activity.getActivityName(), activity.getDescription(), activity.getCategory(),
                activity.getOrganizer(), activity.getVenue(), activity.getStartDate(),
                activity.getEndDate(), activity.getStartTime(), activity.getEndTime(),
                activity.getMaxParticipants(), activity.getStatus(), activity.getId());

        if (affected == 0) {
            throw new ActivityNotFoundException(activity.getId());
        }
        return activity;
    }

    @Override
    public boolean deleteById(Long id) {
        return update("DELETE FROM activities WHERE id = ?", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong("SELECT COUNT(*) FROM activities");
    }

    @Override
    public boolean existsById(Long id) {
        return exists("SELECT COUNT(*) FROM activities WHERE id = ?", id);
    }

    @Override
    public List<CampusActivity> search(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);

        String sql = BASE_SELECT
                + where.getWhere() + " "
                + SqlCriteria.orderBy(criteria.getSort(), SORT_COLUMNS, "start_date") + " "
                + SqlCriteria.direction(criteria.getDirection())
                + " LIMIT ?, ?";

        List<Object> params = new ArrayList<>(where.getParams());
        params.add(criteria.offset());
        params.add(criteria.getSize());

        return query(sql, ACTIVITY_ROW, params.toArray());
    }

    @Override
    public long count(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);
        return queryForLong("SELECT COUNT(*) FROM activities " + where.getWhere(),
                where.getParamsArray());
    }

    private SqlCriteria buildFilter(SearchCriteria criteria) {
        SqlCriteria where = new SqlCriteria();

        if (criteria.hasKeyword()) {
            String like = "%" + criteria.getKeyword().toLowerCase() + "%";
            where.and("(LOWER(activity_name) LIKE ? OR LOWER(organizer) LIKE ?"
                    + " OR LOWER(venue) LIKE ? OR LOWER(category) LIKE ?)",
                    like, like, like, like);
        }

        where.andNotBlank(criteria.filter("category"), "category = ?");
        where.andNotBlank(criteria.filter("status"), "status = ?");
        where.andNotBlank(criteria.filter("organizer"), "organizer = ?");

        // range filters, so the calendar can be narrowed to a period
        where.andNotBlank(criteria.filter("fromDate"), "start_date >= ?");
        where.andNotBlank(criteria.filter("toDate"), "start_date <= ?");

        return where;
    }

    // ==================================================================
    //  Extra look-ups - used by the dashboard and the events page
    // ==================================================================

    /** Events that have not finished yet, ordered by start date (soonest first). */
    public List<CampusActivity> findUpcoming(int limit) {
        return query(BASE_SELECT
                        + " WHERE end_date >= CURRENT_DATE AND status IN ('SCHEDULED','ONGOING')"
                        + " ORDER BY start_date ASC LIMIT ?",
                ACTIVITY_ROW, limit);
    }

    public long countUpcoming() {
        return queryForLong("SELECT COUNT(*) FROM activities "
                + "WHERE end_date >= CURRENT_DATE AND status IN ('SCHEDULED','ONGOING')");
    }

    public List<CampusActivity> findBetween(java.time.LocalDate from, java.time.LocalDate to) {
        return query(BASE_SELECT + " WHERE start_date <= ? AND end_date >= ?"
                        + " ORDER BY start_date ASC",
                ACTIVITY_ROW, to, from);
    }

    public List<String> findDistinctOrganizers() {
        return query("SELECT DISTINCT organizer FROM activities ORDER BY organizer",
                (rs, rowNum) -> str(rs, "organizer"));
    }
}
