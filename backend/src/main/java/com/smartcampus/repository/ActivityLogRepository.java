package com.smartcampus.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.model.ActivityLog;
import com.smartcampus.utility.SqlCriteria;

/**
 * JDBC implementation of the audit-trail table {@code activity_logs}.
 * The same events are also appended to a text file by {@code AuditLogService}
 * (file handling demonstration).
 */
@Repository
public class ActivityLogRepository extends JdbcSupport {

    private static final String BASE_SELECT = """
            SELECT id, user_id, user_name, action, module, details, created_at
            FROM activity_logs """;

    private static final RowMapper<ActivityLog> LOG_ROW = (rs, rowNum) -> new ActivityLog(
            rs.getLong("id"),
            nullableLong(rs, "user_id"),
            str(rs, "user_name"),
            str(rs, "action"),
            str(rs, "module"),
            str(rs, "details"),
            dateTime(rs, "created_at"));

    public ActivityLogRepository(DataSource dataSource) {
        super(dataSource);
    }

    public ActivityLog save(ActivityLog log) {
        long id = insert(
                "INSERT INTO activity_logs (user_id, user_name, action, module, details) VALUES (?,?,?,?,?)",
                log.getUserId(), log.getUserName(), log.getAction(), log.getModule(),
                log.getDetails());
        log.setId(id);
        return log;
    }

    public List<ActivityLog> findRecent(int limit) {
        return query(BASE_SELECT + " ORDER BY created_at DESC LIMIT ?", LOG_ROW, limit);
    }

    public List<ActivityLog> search(SearchCriteria criteria) {
        SqlCriteria where = new SqlCriteria();

        if (criteria.hasKeyword()) {
            String like = "%" + criteria.getKeyword().toLowerCase() + "%";
            where.and("(LOWER(user_name) LIKE ? OR LOWER(action) LIKE ?"
                    + " OR LOWER(module) LIKE ? OR LOWER(details) LIKE ?)",
                    like, like, like, like);
        }
        where.andNotBlank(criteria.filter("module"), "module = ?");
        where.andNotBlank(criteria.filter("action"), "action = ?");

        List<Object> params = new ArrayList<>(where.getParams());
        params.add(criteria.offset());
        params.add(criteria.getSize());

        return query(BASE_SELECT + where.getWhere()
                        + " ORDER BY created_at DESC LIMIT ?, ?",
                LOG_ROW, params.toArray());
    }

    public long count(SearchCriteria criteria) {
        SqlCriteria where = new SqlCriteria();

        if (criteria.hasKeyword()) {
            String like = "%" + criteria.getKeyword().toLowerCase() + "%";
            where.and("(LOWER(user_name) LIKE ? OR LOWER(action) LIKE ?"
                    + " OR LOWER(module) LIKE ? OR LOWER(details) LIKE ?)",
                    like, like, like, like);
        }
        where.andNotBlank(criteria.filter("module"), "module = ?");
        where.andNotBlank(criteria.filter("action"), "action = ?");

        return queryForLong("SELECT COUNT(*) FROM activity_logs " + where.getWhere(),
                where.getParamsArray());
    }

    /** Actions grouped by module - small stat used on the reports page. */
    public Map<String, Long> countByModule() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        query("SELECT module, COUNT(*) AS total FROM activity_logs GROUP BY module ORDER BY module",
                (rs, rowNum) -> {
                    counts.put(str(rs, "module"), rs.getLong("total"));
                    return null;
                });
        return counts;
    }
}
