package com.smartcampus.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything a repository needs in order to run a filtered, sorted,
 * paginated query - assembled from the query string of a GET request.
 *
 * <pre>
 * /api/students?keyword=rahul&amp;department=Computer%20Science&amp;semester=5
 *              &amp;status=ACTIVE&amp;sort=name&amp;direction=asc&amp;page=0&amp;size=10
 * </pre>
 *
 * Filters are kept in a {@link Map} so every module can ask for exactly the
 * keys it understands without changing this class.
 */
public class SearchCriteria {

    public static final int MAX_PAGE_SIZE = 200;

    private String keyword = "";
    private Map<String, String> filters = new HashMap<>();
    private String sort;
    private String direction = "asc";
    private int page = 0;
    private int size = 10;

    public SearchCriteria() {
    }

    public SearchCriteria(String keyword, int page, int size) {
        this.keyword = keyword == null ? "" : keyword;
        this.page = page;
        this.size = size;
        normalise();
    }

    /**
     * Builds a criteria object from the raw query string of a GET request.
     *
     * <p>Every module uses the same convention:</p>
     * <pre>
     *   ?keyword=rahul&amp;department=Computer+Science&amp;semester=5
     *    &amp;sort=name&amp;direction=asc&amp;page=0&amp;size=10
     * </pre>
     *
     * <p>{@code keyword/sort/direction/page/size} control the pager, and every
     * other non-blank parameter becomes a column filter - so a new module can
     * add its own filters without changing this class.</p>
     *
     * @param params all query parameters of the request (may be {@code null})
     */
    public static SearchCriteria from(Map<String, String> params) {
        SearchCriteria criteria = new SearchCriteria();
        if (params == null || params.isEmpty()) {
            criteria.normalise();
            return criteria;
        }

        String keyword = firstNonBlank(params.get("keyword"), params.get("search"));
        criteria.setKeyword(keyword == null ? "" : keyword);

        criteria.setSort(params.get("sort"));
        criteria.setDirection(params.get("direction"));
        criteria.setPage(parseInt(params.get("page"), 0));
        criteria.setSize(parseInt(params.get("size"), 10));

        Map<String, String> filters = new HashMap<>();
        params.forEach((key, value) -> {
            if (key == null || value == null || value.isBlank()) {
                return;
            }
            if (RESERVED_KEYS.contains(key.trim())) {
                return;
            }
            filters.put(key.trim(), value.trim());
        });
        criteria.setFilters(filters);

        criteria.normalise();
        return criteria;
    }

    private static final java.util.Set<String> RESERVED_KEYS = java.util.Set.of(
            "keyword", "search", "sort", "direction", "page", "size");

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** Trim / bound the paging values so a client cannot request everything. */
    public void normalise() {
        if (keyword == null) {
            keyword = "";
        }
        keyword = keyword.trim();
        if (page < 0) {
            page = 0;
        }
        if (size < 1) {
            size = 10;
        }
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        if (direction == null || direction.isBlank()) {
            direction = "asc";
        }
        direction = direction.equalsIgnoreCase("desc") ? "desc" : "asc";
    }

    // ------------------------------------------------------------------
    // Filter helpers - used by the repositories
    // ------------------------------------------------------------------

    /** Raw filter value, or {@code null} when the filter was not supplied. */
    public String filter(String key) {
        String value = filters.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Filter value with a fallback. */
    public String filter(String key, String fallback) {
        String value = filter(key);
        return value == null ? fallback : value;
    }

    /** Integer filter with a fallback (returns the fallback when absent/invalid). */
    public int intFilter(String key, int fallback) {
        String value = filter(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isEmpty();
    }

    /** SQL LIMIT offset for the current page. */
    public int offset() {
        return page * size;
    }

    // ------------------------------------------------------------------

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Map<String, String> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, String> filters) {
        this.filters = filters == null ? new HashMap<>() : filters;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return "SearchCriteria{keyword='" + keyword + "', filters=" + filters
                + ", sort='" + sort + "', " + direction + ", page=" + page + ", size=" + size + "}";
    }
}
