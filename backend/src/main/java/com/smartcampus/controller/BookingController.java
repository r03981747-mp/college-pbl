package com.smartcampus.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartcampus.dto.ApiResponse;
import com.smartcampus.dto.AvailabilityRequest;
import com.smartcampus.dto.AvailabilityResponse;
import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.model.Booking;
import com.smartcampus.model.Classroom;
import com.smartcampus.model.Equipment;
import com.smartcampus.model.Laboratory;
import com.smartcampus.model.enums.BookingStatus;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.BookingService;
import com.smartcampus.service.ClassroomService;
import com.smartcampus.service.EquipmentService;
import com.smartcampus.service.LaboratoryService;

/**
 * REST API of the resource booking module - the core of the application.
 *
 * <pre>
 * GET    /api/bookings                    list + search + filter + sort + page
 * GET    /api/bookings/my                 bookings of the signed-in user
 * GET    /api/bookings/resources          every bookable resource (drop-down)
 * GET    /api/bookings/search             keyword search
 * GET    /api/bookings/{id}               one record (ownership enforced)
 * POST   /api/bookings                    create a booking      201 / 409 on conflict
 * POST   /api/bookings/availability       "is this slot free?"
 * PUT    /api/bookings/{id}/status        approve / reject / complete
 * POST   /api/bookings/{id}/cancel        cancel                200
 * DELETE /api/bookings/{id}               hard delete (admin)   200
 * </pre>
 *
 * <p>A conflicting request answers with <b>HTTP 409</b> and a message such as
 * <i>"Booking conflict: LAB-02 - Database Systems Lab is already booked from 10:00 to
 * 12:00 (BK-0003). Please choose a different time."</i></p>
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    /** Body of the status-change action. */
    public record StatusRequest(BookingStatus status) {
    }

    private final BookingService bookingService;
    private final ClassroomService classroomService;
    private final LaboratoryService laboratoryService;
    private final EquipmentService equipmentService;

    public BookingController(BookingService bookingService,
                             ClassroomService classroomService,
                             LaboratoryService laboratoryService,
                             EquipmentService equipmentService) {
        this.bookingService = bookingService;
        this.classroomService = classroomService;
        this.laboratoryService = laboratoryService;
        this.equipmentService = equipmentService;
    }

    // ------------------------------------------------------------------
    //  Read
    // ------------------------------------------------------------------

    /** Students automatically receive only their own bookings. */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<Booking>>> list(
            @RequestParam Map<String, String> params) {
        PagedResponse<Booking> page = bookingService.list(SearchCriteria.from(params));
        return ResponseEntity.ok(ApiResponse.ok(page,
                page.getTotalElements() + " booking(s) found"));
    }

    /** Bookings created by the signed-in user. */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<Booking>>> myBookings() {
        List<Booking> bookings = bookingService.myBookings();
        return ResponseEntity.ok(ApiResponse.ok(bookings, bookings.size() + " booking(s)"));
    }

    /** Flat list of every bookable resource, used by the booking form drop-down. */
    @GetMapping("/resources")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> resources() {
        List<Map<String, Object>> options = new ArrayList<>();

        for (Classroom room : classroomService.search("", 500)) {
            options.add(option("CLASSROOM", room.getId(), room.getResourceLabel(),
                    room.getStatus() == null ? "" : room.getStatus().name()));
        }
        for (Laboratory lab : laboratoryService.search("", 500)) {
            options.add(option("LABORATORY", lab.getId(), lab.getResourceLabel(),
                    lab.getStatus() == null ? "" : lab.getStatus().name()));
        }
        for (Equipment item : equipmentService.search("", 500)) {
            options.add(option("EQUIPMENT", item.getId(), item.getResourceLabel(),
                    item.getStatus() == null ? "" : item.getStatus().name()));
        }

        return ResponseEntity.ok(ApiResponse.ok(options, options.size() + " resource(s)"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Booking>>> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        List<Booking> results = bookingService.search(keyword, limit);
        return ResponseEntity.ok(ApiResponse.ok(results, results.size() + " match(es)"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Booking>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.getVisible(id), "Booking found"));
    }

    // ------------------------------------------------------------------
    //  Write
    // ------------------------------------------------------------------

    /**
     * Creates a booking.
     *
     * <p>Returns <b>201</b> on success and <b>409</b> when another booking
     * already occupies the slot - the check and the insert run inside one
     * synchronised, transactional critical section.</p>
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Booking>> create(@RequestBody Booking request) {
        Booking saved = bookingService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(saved, "Booking created successfully: "
                        + saved.getBookingRef() + " for " + saved.getResourceName()));
    }

    /** Asks whether a resource is free for the given window. */
    @PostMapping("/availability")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> availability(
            @RequestBody AvailabilityRequest request) {
        AvailabilityResponse availability = bookingService.checkAvailability(request);
        return ResponseEntity.ok(ApiResponse.ok(availability, availability.getMessage()));
    }

    /** Approve / reject / complete - administrators and faculty only. */
    @PutMapping("/{id}/status")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Booking>> changeStatus(
            @PathVariable Long id, @RequestBody StatusRequest body) {
        Booking updated = bookingService.changeStatus(id, body.status());
        return ResponseEntity.ok(ApiResponse.ok(updated,
                updated.getBookingRef() + " is now " + updated.getStatus().getLabel()));
    }

    /** Cancel - available to the owner, administrators and faculty members. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Booking>> cancel(@PathVariable Long id) {
        Booking cancelled = bookingService.cancel(id);
        return ResponseEntity.ok(ApiResponse.ok(cancelled,
                "Booking " + cancelled.getBookingRef() + " cancelled."));
    }

    @DeleteMapping("/{id}")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        bookingService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Booking deleted successfully."));
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> option(String type, Long id, String label, String status) {
        Map<String, Object> option = new java.util.LinkedHashMap<>();
        option.put("type", type);
        option.put("id", id);
        option.put("label", label);
        option.put("status", status);
        return option;
    }
}
