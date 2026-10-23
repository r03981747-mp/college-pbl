package com.smartcampus.repository;

import java.util.List;

import com.smartcampus.dto.SearchCriteria;

/**
 * Paged / filtered / sorted query contract.
 *
 * <p>Kept separate from {@link GenericRepository} so a repository can support
 * rich searching without inheriting write operations it does not need
 * (interface segregation).</p>
 *
 * @param <T> entity type produced by the queries
 */
public interface SearchableRepository<T> {

    /** One page of matching rows (already filtered, sorted and limited by SQL). */
    List<T> search(SearchCriteria criteria);

    /** Total number of rows matching the same filters (used for the pager). */
    long count(SearchCriteria criteria);
}
