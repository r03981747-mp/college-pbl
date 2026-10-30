package com.smartcampus.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.smartcampus.exception.SmartCampusException;

/**
 * Thin hand-written JDBC layer used by every repository.
 *
 * <p><b>This is the class to show during the viva when asked "where is your
 * JDBC?".</b>  It uses the plain {@code java.sql} API:</p>
 *
 * <pre>
 *   DataSource  ->  Connection  ->  PreparedStatement  ->  ResultSet
 * </pre>
 *
 * <p><b>SQL injection:</b> every value is bound with
 * {@link PreparedStatement#setObject} - user input is <u>never</u>
 * concatenated into the SQL string.  Only identifiers that cannot be bound
 * (ORDER BY columns) are whitelisted in {@link SqlCriteria}.</p>
 *
 * <p><b>OOP:</b> abstract base class (cannot be instantiated on its own) that
 * the concrete repositories inherit from, with a nested functional
 * {@link RowMapper} interface used through lambda expressions.</p>
 */
public abstract class JdbcSupport {

    private static final Logger log = LoggerFactory.getLogger(JdbcSupport.class);

    protected final DataSource dataSource;

    protected JdbcSupport(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ==================================================================
    //  Functional row mapper - implemented as a LAMBDA in each repository
    // ==================================================================
    @FunctionalInterface
    public interface RowMapper<T> {
        T mapRow(ResultSet resultSet, int rowNum) throws SQLException;
    }

    // ==================================================================
    //  Query helpers
    // ==================================================================

    /** Run a SELECT and map every row with the supplied lambda. */
    protected <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            bind(statement, params);

            try (ResultSet resultSet = statement.executeQuery()) {
                int row = 0;
                while (resultSet.next()) {
                    results.add(mapper.mapRow(resultSet, ++row));
                }
            }
        } catch (SQLException ex) {
            throw fail("read", sql, ex);
        }
        return results;
    }

    /** Run a SELECT expected to return at most one row. */
    protected <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = query(sql, mapper, params);
        return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.get(0));
    }

    /** Run a SELECT that returns a single scalar (usually COUNT(*)) . */
    protected long queryForLong(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw fail("count", sql, ex);
        }
    }

    /** Execute an INSERT and return the auto-generated primary key. */
    protected long insert(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            bind(statement, params);

            if (statement.executeUpdate() == 0) {
                throw new SmartCampusException("The record could not be saved - no row was inserted.");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SmartCampusException("The record was saved but no generated id was returned.");
            }
        } catch (SQLException ex) {
            throw fail("insert", sql, ex);
        }
    }

    /** Execute an UPDATE / DELETE and return the affected row count. */
    protected int update(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw fail("update", sql, ex);
        }
    }

    /** {@code true} when at least one row matches the given WHERE clause. */
    protected boolean exists(String sql, Object... params) {
        return queryForLong(sql, params) > 0;
    }

    // ==================================================================
    //  Small JDBC utilities reused by the row mappers
    // ==================================================================

    /** Binds all parameters, converting enums to their SQL name. */
    protected static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            if (value instanceof Enum<?> enumValue) {
                value = enumValue.name();           // Java 17 pattern matching
            }
            statement.setObject(i + 1, value);
        }
    }

    /** Null-safe {@code getString}. */
    protected static String str(ResultSet rs, String column) throws SQLException {
        return rs.getString(column);
    }

    /** Null-safe {@code getLong} for nullable columns (returns {@code Long}). */
    protected static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /** Null-safe {@code getInt}. */
    protected static int intOf(ResultSet rs, String column) throws SQLException {
        return rs.getInt(column);
    }

    /** Null-safe {@code getBigDecimal} for nullable numeric columns. */
    protected static java.math.BigDecimal nullableDecimal(ResultSet rs, String column)
            throws SQLException {
        return rs.getBigDecimal(column);
    }

    /** {@code getBigDecimal} with a defensive zero when the column is NULL. */
    protected static java.math.BigDecimal decimal(ResultSet rs, String column)
            throws SQLException {
        java.math.BigDecimal value = rs.getBigDecimal(column);
        return value == null ? java.math.BigDecimal.ZERO : value;
    }

    /** Null-safe {@code getDate} -> {@link LocalDate}. */
    protected static LocalDate date(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    /** Null-safe {@code getTime} -> {@link LocalTime}. */
    protected static LocalTime time(ResultSet rs, String column) throws SQLException {
        java.sql.Time value = rs.getTime(column);
        return value == null ? null : value.toLocalTime();
    }

    /** Null-safe {@code getTimestamp} -> {@link LocalDateTime}. */
    protected static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    /**
     * Converts a database VARCHAR into an enum constant, falling back to the
     * supplied default when the column is NULL or contains an unknown value.
     */
    protected static <E extends Enum<E>> E enumOf(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    /** Wraps a low level {@link SQLException} in a clean business exception. */
    private SmartCampusException fail(String action, String sql, SQLException ex) {
        // The SQL itself must never reach the browser, but it belongs in the
        // server log so a syntax problem can be diagnosed in one glance.
        log.error("JDBC {} failed: {}\nSQL: {}\nCause: {}", action,
                ex.getMessage(), sql, ex.getMessage());
        return new SmartCampusException(
                "Database " + action + " failed. See the server log for details.", ex);
    }
}
