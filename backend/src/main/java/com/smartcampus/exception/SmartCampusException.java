package com.smartcampus.exception;

/**
 * Root of the Smart Campus exception hierarchy.
 *
 * <p><b>OOP concepts:</b> <i>inheritance</i> (every domain exception extends
 * this class), <i>method overloading</i> (two constructors) and
 * <i>exception handling</i> ({@code throw}/{@code throws}/{@code try}/{@code catch}/{@code finally}).</p>
 *
 * <p>It extends {@link RuntimeException} (unchecked) so business methods do not
 * have to declare it in a long {@code throws} clause, while the global
 * exception handler below still converts it into a clean HTTP response.</p>
 */
public class SmartCampusException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SmartCampusException(String message) {
        super(message);
    }

    /** Overloaded constructor - keeps the original cause for logging. */
    public SmartCampusException(String message, Throwable cause) {
        super(message, cause);
    }
}
