package com.smartcampus.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Uniform envelope returned by <b>every</b> REST endpoint.
 *
 * <p><b>OOP concept:</b> <i>generics</i> - the same class carries any payload
 * type {@code T} (a student, a list of bookings, a dashboard, or nothing at
 * all when {@code T} is {@code Void}).</p>
 *
 * <pre>
 * { "success": true, "message": "...", "data": { ... }, "timestamp": "..." }
 * </pre>
 */
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private Map<String, String> errors;
    private LocalDateTime timestamp;

    /** No-argument constructor (Jackson). */
    public ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    /** Convenience constructor used by the factory methods. */
    private ApiResponse(boolean success, String message, T data, Map<String, String> errors) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errors = errors;
        this.timestamp = LocalDateTime.now();
    }

    // ------------------------------------------------------------------
    // Generic factory methods  (static + generic = clean client code)
    // ------------------------------------------------------------------

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public static <T> ApiResponse<T> error(String message, Map<String, String> fieldErrors) {
        return new ApiResponse<>(false, message, null, fieldErrors);
    }

    // ------------------------------------------------------------------

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
