package com.smartcampus.utility;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import com.smartcampus.exception.InvalidInputException;

/**
 * Static input-validation helpers used by every service before a record is
 * written to MySQL.
 *
 * <p><b>Why validate twice?</b> The browser checks fields for convenience, but
 * the browser can be bypassed (curl, Postman, a tampered page).  The server is
 * the only place where validation actually counts.</p>
 *
 * <p>Each method either returns the cleaned value or throws
 * {@link InvalidInputException}, which {@code GlobalExceptionHandler} turns
 * into an HTTP 400 with a friendly message.</p>
 */
public final class Validators {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");

    private Validators() {
        // utility class - not instantiable
    }

    /** Required, non-blank text. Returns the trimmed value. */
    public static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(field, "is required");
        }
        return value.trim();
    }

    /** Optional text - returns {@code null} instead of an empty string. */
    public static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new InvalidInputException(field,
                    "must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    /** Required text with a maximum length. */
    public static String requiredText(String value, String field, int maxLength) {
        String trimmed = text(value, field);
        if (trimmed.length() > maxLength) {
            throw new InvalidInputException(field,
                    "must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    /** Required, syntactically valid e-mail address. */
    public static String email(String value, String field) {
        String trimmed = text(value, field);
        if (!EMAIL.matcher(trimmed).matches()) {
            throw new InvalidInputException(field, "must be a valid e-mail address");
        }
        if (trimmed.length() > 120) {
            throw new InvalidInputException(field, "must be at most 120 characters");
        }
        return trimmed;
    }

    /** Optional e-mail address (validated only when supplied). */
    public static String optionalEmail(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return email(value, field);
    }

    /**
     * Required phone number: 7-15 digits after stripping spaces, dashes,
     * brackets and a leading {@code +}.
     */
    public static String phone(String value, String field) {
        String trimmed = text(value, field);
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() < 7 || digits.length() > 15 || !HAS_DIGIT.matcher(digits).matches()) {
            throw new InvalidInputException(field,
                    "must be a valid phone number (7-15 digits)");
        }
        if (trimmed.length() > 20) {
            throw new InvalidInputException(field, "must be at most 20 characters");
        }
        return trimmed;
    }

    /** Required integer inside an inclusive range. */
    public static int range(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new InvalidInputException(field,
                    "must be between " + min + " and " + max);
        }
        return value;
    }

    /** Required integer above a minimum. */
    public static int atLeast(int value, int min, String field) {
        if (value < min) {
            throw new InvalidInputException(field, "must be " + min + " or more");
        }
        return value;
    }

    /** Required, non-null date. */
    public static LocalDate date(LocalDate value, String field) {
        if (value == null) {
            throw new InvalidInputException(field, "is required");
        }
        return value;
    }

    /** Date that must not be in the future (date of birth, joining date...). */
    public static LocalDate notInFuture(LocalDate value, String field) {
        LocalDate checked = date(value, field);
        if (checked.isAfter(LocalDate.now())) {
            throw new InvalidInputException(field, "cannot be a future date");
        }
        return checked;
    }

    /** Date that must not be in the past (booking date). */
    public static LocalDate notInPast(LocalDate value, String field) {
        LocalDate checked = date(value, field);
        if (checked.isBefore(LocalDate.now())) {
            throw new InvalidInputException(field, "cannot be a date in the past");
        }
        return checked;
    }

    /** Parses a free-form date and converts parse errors into a clean message. */
    public static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(field, "is required");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new InvalidInputException(field, "must use the format YYYY-MM-DD");
        }
    }

    /** Ensures {@code end} is strictly after {@code start}. */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidInputException(message);
        }
    }
}
