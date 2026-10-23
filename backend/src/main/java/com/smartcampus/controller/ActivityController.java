package com.smartcampus.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
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
import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.model.CampusActivity;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.ActivityService;

/**
 * REST API of the campus activity / event module.
 *
 * <pre>
 * GET    /api/activities                  list + search + filter + sort + page
 * GET    /api/activities/upcoming         next events (events page + dashboard)
 * GET    /api/activities/search           keyword search
 * GET    /api/activities/options          organizers for the filter drop-down
 * GET    /api/activities/{id}             one record
 * POST   /api/activities                  create   201
 * PUT    /api/activities/{id}             update   200
 * DELETE /api/activities/{id}             delete   200
 * </pre>
 */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CampusActivity>>> list(
            @RequestParam Map<String, String> params) {
        PagedResponse<CampusActivity> page = activityService.list(SearchCriteria.from(params));
        return ResponseEntity.ok(ApiResponse.ok(page,
                page.getTotalElements() + " activity/activities found"));
    }

    /** Next events, soonest first - powers the event cards on the page. */
    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<CampusActivity>>> upcoming(
            @RequestParam(defaultValue = "6") int limit) {
        List<CampusActivity> events = activityService.upcoming(limit);
        return ResponseEntity.ok(ApiResponse.ok(events, events.size() + " upcoming event(s)"));
    }

    /** Events active on a specific day (calendar navigation). */
    @GetMapping("/on-date")
    public ResponseEntity<ApiResponse<List<CampusActivity>>> onDate(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<CampusActivity> events = activityService.onDate(date);
        return ResponseEntity.ok(ApiResponse.ok(events, events.size() + " event(s) on that date"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<CampusActivity>>> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        List<CampusActivity> results = activityService.search(keyword, limit);
        return ResponseEntity.ok(ApiResponse.ok(results, results.size() + " match(es)"));
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> options() {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("organizers", activityService.organizers()), "Filter options"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CampusActivity>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(activityService.getById(id), "Activity found"));
    }

    @PostMapping
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<CampusActivity>> create(
            @RequestBody CampusActivity activity) {
        CampusActivity saved = activityService.create(activity);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(saved, "Activity created successfully."));
    }

    @PutMapping("/{id}")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<CampusActivity>> update(
            @PathVariable Long id, @RequestBody CampusActivity activity) {
        CampusActivity saved = activityService.update(id, activity);
        return ResponseEntity.ok(ApiResponse.ok(saved, "Activity updated successfully."));
    }

    @DeleteMapping("/{id}")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        activityService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Activity deleted successfully."));
    }
}
