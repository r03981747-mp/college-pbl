package com.smartcampus.controller;

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
import com.smartcampus.model.Classroom;
import com.smartcampus.model.enums.ResourceType;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.ClassroomService;

/**
 * REST API of the classroom module (rooms, seminar halls, auditoriums...).
 *
 * <pre>
 * GET    /api/classrooms              list + search + filter + sort + page
 * GET    /api/classrooms/search       keyword search
 * GET    /api/classrooms/options      buildings for the filter drop-down
 * GET    /api/classrooms/{id}         one record
 * POST   /api/classrooms              create   201
 * PUT    /api/classrooms/{id}         update   200
 * DELETE /api/classrooms/{id}         delete   200
 * POST   /api/classrooms/{id}/availability   "is it free?" check
 * </pre>
 */
@RestController
@RequestMapping("/api/classrooms")
public class ClassroomController {

    private final ClassroomService classroomService;

    public ClassroomController(ClassroomService classroomService) {
        this.classroomService = classroomService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<Classroom>>> list(
            @RequestParam Map<String, String> params) {
        PagedResponse<Classroom> page = classroomService.list(SearchCriteria.from(params));
        return ResponseEntity.ok(ApiResponse.ok(page,
                page.getTotalElements() + " room(s) found"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Classroom>>> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        List<Classroom> results = classroomService.search(keyword, limit);
        return ResponseEntity.ok(ApiResponse.ok(results, results.size() + " match(es)"));
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> options() {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("buildings", classroomService.buildings()), "Filter options"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Classroom>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(classroomService.getById(id), "Room found"));
    }

    @PostMapping
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Classroom>> create(@RequestBody Classroom classroom) {
        Classroom saved = classroomService.create(classroom);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(saved, "Classroom added successfully."));
    }

    @PutMapping("/{id}")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Classroom>> update(
            @PathVariable Long id, @RequestBody Classroom classroom) {
        Classroom saved = classroomService.update(id, classroom);
        return ResponseEntity.ok(ApiResponse.ok(saved, "Classroom updated successfully."));
    }

    @DeleteMapping("/{id}")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        classroomService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Classroom deleted successfully."));
    }

    /**
     * "Check availability" action on the classroom page.
     * Answers with a human readable sentence, e.g.
     * <i>"C-101 - Main Building is already booked from 10:00 to 12:00 (BK-0003)"</i>.
     */
    @PostMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> availability(
            @PathVariable Long id, @RequestBody AvailabilityRequest request) {
        request.setResourceId(id);
        request.setResourceType(ResourceType.CLASSROOM);
        AvailabilityResponse availability = classroomService.checkAvailability(request);
        return ResponseEntity.ok(ApiResponse.ok(availability, availability.getMessage()));
    }
}
