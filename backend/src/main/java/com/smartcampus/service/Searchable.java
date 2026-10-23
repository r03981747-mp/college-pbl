package com.smartcampus.service;

import java.util.List;

/**
 * Contract for any service that can be searched by keyword.
 *
 * <p><b>OOP concepts:</b> <i>generics</i> and <i>method overloading</i> - the
 * same operation is offered with one or two arguments, each with its own
 * meaning.</p>
 *
 * @param <T> entity type produced by the searches
 */
public interface Searchable<T> {

    /**
     * Keyword search across every searchable column of the module
     * (e.g. for a student: USN, name, e-mail, department).
     *
     * @param keyword free text entered by the user; blank returns everything
     * @return matching records, never {@code null}
     */
    List<T> search(String keyword);

    /**
     * Overloaded search - identical to {@link #search(String)} but capped at
     * {@code maxResults} rows.  Used for type-ahead suggestions where only a
     * handful of matches are needed.
     *
     * @param keyword     free text entered by the user
     * @param maxResults  maximum number of rows to return (1 or more)
     */
    List<T> search(String keyword, int maxResults);
}
