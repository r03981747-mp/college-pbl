package com.smartcampus.utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Builds a parameterised {@code WHERE} clause at runtime.
 *
 * <p>Dynamic search boxes need a different WHERE for every request, but the
 * <b>values</b> must always be bound placeholders.  This class therefore
 * collects the SQL fragments together with their {@code ?} parameters so the
 * repository can hand both to a {@code PreparedStatement}:</p>
 *
 * <pre>
 *   criteria.and("usn LIKE ?", "%1CS21%");
 *   criteria.and("semester = ?", 5);
 *   --&gt; "WHERE usn LIKE ? AND semester = ?"  with params ["%1CS21%", 5]
 * </pre>
 *
 * <p><b>Security:</b> values are always bound, never concatenated.  The only
 * thing that cannot be bound is an identifier such as an ORDER BY column, so
 * {@link #orderBy} accepts <b>only whitelisted column names</b>.</p>
 */
public final class SqlCriteria {

    private final StringBuilder clause = new StringBuilder();
    private final List<Object> params = new ArrayList<>();

    /** Append {@code AND <condition>} with its bound values. */
    public SqlCriteria and(String condition, Object... values) {
        if (clause.length() > 0) {
            clause.append(" AND ");
        }
        clause.append(condition);
        if (values != null) {
            for (Object value : values) {
                params.add(value);
            }
        }
        return this;
    }

    /** Append the condition only when the flag is true. */
    public SqlCriteria andIf(boolean condition, String sql, Object... values) {
        if (condition) {
            and(sql, values);
        }
        return this;
    }

    /** Append the condition only when the supplied filter value is present. */
    public SqlCriteria andNotBlank(String value, String conditionWithPlaceholder) {
        if (value != null && !value.isBlank()) {
            and(conditionWithPlaceholder, value.trim());
        }
        return this;
    }

    /** Append the condition only when the supplied object is not {@code null}. */
    public SqlCriteria andNotNull(Object value, String conditionWithPlaceholder) {
        if (value != null) {
            and(conditionWithPlaceholder, value);
        }
        return this;
    }

    /**
     * Append a numeric condition only when the filter value is a valid number.
     * Malformed input (e.g. {@code ?semester=abc}) is silently ignored instead
     * of reaching MySQL as a broken comparison.
     */
    public SqlCriteria andNumeric(String value, String conditionWithPlaceholder) {
        if (value == null || value.isBlank()) {
            return this;
        }
        try {
            and(conditionWithPlaceholder, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            // not a number - drop the filter rather than failing the request
        }
        return this;
    }

    /** Append a condition only when the key exists in the filter map. */
    public SqlCriteria andHas(Map<String, String> filters, String key,
                              String conditionWithPlaceholder) {
        if (filters == null) {
            return this;
        }
        return andNotBlank(filters.get(key), conditionWithPlaceholder);
    }

    /** The full predicate - {@code 1=1} when nothing was added, so WHERE is always valid. */
    public String getClause() {
        return clause.length() == 0 ? "1=1" : clause.toString();
    }

    /**
     * The complete {@code WHERE ...} fragment (including the keyword itself).
     *
     * <p>It deliberately starts with a <b>space</b>.  Callers concatenate it
     * straight after a table name taken from a {@code BASE_SELECT} text block,
     * and a text block never keeps the trailing blank of its last line - so
     * without the leading space MySQL would receive
     * {@code FROM studentsWHERE 1=1} and reject the statement.  A harmless
     * double space when the caller already ends with one.</p>
     */
    public String getWhere() {
        return " WHERE " + getClause();
    }

    public List<Object> getParams() {
        return params;
    }

    public Object[] getParamsArray() {
        return params.toArray();
    }

    public boolean isEmpty() {
        return clause.length() == 0;
    }

    // ==================================================================
    //  ORDER BY - the only place where SQL text is generated from input,
    //  therefore a whitelist is mandatory.
    // ==================================================================

    /**
     * Builds a safe {@code ORDER BY} fragment.
     *
     * @param requestedSort the column name requested by the UI (may be garbage)
     * @param allowed       map of allowed key -&gt; real database column
     * @param fallback      column used when the request is empty / not allowed
     * @return e.g. {@code "ORDER BY name ASC"}
     */
    public static String orderBy(String requestedSort, Map<String, String> allowed, String fallback) {
        String column = requestedSort == null ? null : requestedSort.trim();
        String realColumn = (column == null || column.isEmpty()) ? null : allowed.get(column);
        if (realColumn == null) {
            realColumn = allowed.getOrDefault(fallback, fallback);
        }
        return "ORDER BY " + realColumn;
    }

    /** Same as above but the allowed values are already database columns. */
    public static String orderBy(String requestedSort, Collection<String> allowed, String fallback) {
        String column = requestedSort == null ? "" : requestedSort.trim();
        if (column.isEmpty() || !allowed.contains(column)) {
            return "ORDER BY " + fallback;
        }
        return "ORDER BY " + column;
    }

    /** Normalises a sort direction to SQL ({@code ASC} / {@code DESC}). */
    public static String direction(String requested) {
        return "desc".equalsIgnoreCase(requested) ? "DESC" : "ASC";
    }

    /** Comma separated column list used inside {@code IN (...)} clauses. */
    public static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(Math.max(1, count), "?"));
    }
}
