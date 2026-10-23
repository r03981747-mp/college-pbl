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
import com.smartcampus.model.Laboratory;
import com.smartcampus.model.enums.ResourceType;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.LaboratoryService;

/**
 * REST API of the laboratory module.
 *
 * <pre>
 * GET    /api/laboratories              list + search + filter + sort + page
 * GET    /api/laboratories/search       keyword search
 * GET    /api/laboratories/options      departments / lab types
 * GET    /api/laboratories/{id}         one record
 * POST   /api/laboratories              create   201
 * PUT    /api/laboratories/{id}         update   200
 * DELETE /api/laboratories/{id}         delete   200
 * POST   /api/laboratories/{id}/availability   "is it free?" check
 * </pre>
 */
@RestController
@RequestMapping("/api/laboratories")
public class LaboratoryController {

    private final LaboratoryService laboratoryService;

    public LaboratoryController(LaboratoryService laboratoryService) {
        this.laboratoryService = laboratoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<Laboratory>>> list(
            @RequestParam Map<String, String> params) {
        PagedResponse<Laboratory> page = laboratoryService.list(SearchCriteria.from(params));
        return ResponseEntity.ok(ApiResponse.ok(page,
                page.getTotalElements() + " laboratory/laboratories found"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Laboratory>>> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        List<Laboratory> results = laboratoryService.search(keyword, limit);
        return ResponseEntity.ok(ApiResponse.ok(results, results.size() + " match(es)"));
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> options() {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("departments", laboratoryService.departments(),
                       "labTypes", laboratoryService.labTypes()),
                "Filter options"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Laboratory>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(laboratoryService.getById(id), "Laboratory found"));
    }

    @PostMapping
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Laboratory>> create(@RequestBody Laboratory laboratory) {
        Laboratory saved = laboratoryService.create(laboratory);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(saved, "Laboratory added successfully."));
    }

    @PutMapping("/{id}")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Laboratory>> update(
            @PathVariable Long id, @RequestBody Laboratory laboratory) {
        Laboratory saved = laboratoryService.update(id, laboratory);
        return ResponseEntity.ok(ApiResponse.ok(saved, "Laboratory updated successfully."));
    }

    @DeleteMapping("/{id}")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        laboratoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Laboratory deleted successfully."));
    }

    /** "Check availability" action on the laboratory page. */
    @PostMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> availability(
            @PathVariable Long id, @RequestBody AvailabilityRequest request) {
        request.setResourceId(id);
        request.setResourceType(ResourceType.LABORATORY);
        AvailabilityResponse availability = laboratoryService.checkAvailability(request);
        return ResponseEntity.ok(ApiResponse.ok(availability, availability.getMessage()));
    }
}
