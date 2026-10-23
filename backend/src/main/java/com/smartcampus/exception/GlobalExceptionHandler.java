package com.smartcampus.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.smartcampus.dto.ApiResponse;

/**
 * Translates every Java exception into a clean, consistent JSON response.
 *
 * <p><b>Rule:</b> the browser must never see a stack trace.  Detailed
 * information is written to the server log; the client receives only a
 * friendly message.</p>
 *
 * <pre>
 * HTTP 400  invalid input          -> { success:false, message:"..." }
 * HTTP 401  not logged in          -> { success:false, message:"..." }
 * HTTP 403  wrong role             -> { success:false, message:"..." }
 * HTTP 404  record missing         -> { success:false, message:"..." }
 * HTTP 409  duplicate / conflict   -> { success:false, message:"..." }
 * HTTP 500  anything unexpected    -> { success:false, message:"..." }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ------------------------------------------------------------------
    // 404 - record not found
    // ------------------------------------------------------------------
    @ExceptionHandler({
            StudentNotFoundException.class,
            FacultyNotFoundException.class,
            CourseNotFoundException.class,
            ResourceNotFoundException.class,
            ActivityNotFoundException.class,
            UserNotFoundException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleNotFound(SmartCampusException ex) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // ------------------------------------------------------------------
    // 409 - conflict (duplicate key or overlapping booking)
    // ------------------------------------------------------------------
    @ExceptionHandler({DuplicateRecordException.class, BookingConflictException.class})
    public ResponseEntity<ApiResponse<Void>> handleConflict(SmartCampusException ex) {
        return respond(HttpStatus.CONFLICT, ex.getMessage());
    }

    // ------------------------------------------------------------------
    // 400 - validation / bad input
    // ------------------------------------------------------------------
    @ExceptionHandler({InvalidBookingException.class, InvalidInputException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(SmartCampusException ex) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Bean Validation failures on a DTO ({@code @NotBlank}, {@code @Email}...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBeanValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        String message = fieldErrors.isEmpty()
                ? "Please correct the highlighted fields."
                : fieldErrors.values().iterator().next();
        ApiResponse<Void> body = ApiResponse.error(message, fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Body JSON could not be parsed (wrong date format, unknown enum value...). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST,
                "Invalid request format. Please check the date/time values you entered.");
    }

    // ------------------------------------------------------------------
    // 404 / 405 / 415 - routing mistakes (missing route, wrong HTTP verb)
    // ------------------------------------------------------------------
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND,
                "The requested API endpoint does not exist.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED,
                "This endpoint does not accept the " + ex.getMethod()
                        + " method. Check the API documentation.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMedia(
            HttpMediaTypeNotSupportedException ex) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Please send the request body as application/json.");
    }

    // ------------------------------------------------------------------
    // 401 / 403 - authentication and authorization
    // ------------------------------------------------------------------
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return respond(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // ------------------------------------------------------------------
    // 409 - database constraint violations that were not anticipated
    // ------------------------------------------------------------------
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Database constraint violated: {}", ex.getMessage());
        return respond(HttpStatus.CONFLICT,
                "The record could not be saved because it would break a database constraint "
                        + "(duplicate value or a related record still uses it).");
    }

    // ------------------------------------------------------------------
    // 500 - base exception thrown by the repository layer (SQL failures...)
    // ------------------------------------------------------------------
    @ExceptionHandler(SmartCampusException.class)
    public ResponseEntity<ApiResponse<Void>> handleBase(SmartCampusException ex) {
        log.error("Business layer error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                "A database error occurred while processing your request. Please try again.");
    }

    // ------------------------------------------------------------------
    // 500 - anything unexpected
    // ------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        // full detail for the developer in the server console - never to the browser
        log.error("Unhandled server error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on the server. Please try again.");
    }

    // ------------------------------------------------------------------

    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String message) {
        ApiResponse<Void> body = ApiResponse.error(message);
        body.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(status).body(body);
    }
}
