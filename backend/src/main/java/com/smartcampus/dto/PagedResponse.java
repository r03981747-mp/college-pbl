package com.smartcampus.dto;

import java.util.Collections;
import java.util.List;

/**
 * Server-side pagination envelope.
 *
 * <p><b>OOP concept:</b> <i>generics</i> - reused unchanged for students,
 * faculty, courses, resources, activities and bookings.</p>
 */
public class PagedResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public PagedResponse() {
        this.content = Collections.emptyList();
    }

    public PagedResponse(List<T> content, int page, int size, long totalElements) {
        this.content = content == null ? Collections.emptyList() : content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        this.hasNext = page + 1 < totalPages;
        this.hasPrevious = page > 0;
    }

    /** Factory that derives the paging flags from the requested page number. */
    public static <T> PagedResponse<T> of(List<T> content, SearchCriteria criteria, long total) {
        return new PagedResponse<>(content, criteria.getPage(), criteria.getSize(), total);
    }

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
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

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    public boolean isHasPrevious() {
        return hasPrevious;
    }

    public void setHasPrevious(boolean hasPrevious) {
        this.hasPrevious = hasPrevious;
    }
}
