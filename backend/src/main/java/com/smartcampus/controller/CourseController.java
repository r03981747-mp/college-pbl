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
import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.model.Course;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.CourseService;

/**
 * REST API of the course module.
 *
 * <pre>
 * GET    /api/courses                list + search + filter + sort + page
 * GET    /api/courses/search         keyword search
 * GET    /api/courses/options        departments for the filter drop-down
 * GET    /api/courses/unassigned     courses with no teacher yet
 * GET    /api/courses/{id}           one record
 * POST   /api/courses                create                201
 * PUT    /api/courses/{id}           update                200
 * PUT    /api/courses/{id}/faculty   assign a teacher     200
 * DELETE /api/courses/{id}           delete                200
 * </pre>
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<Course>>> list(
            @RequestParam Map<String, String> params) {
        PagedResponse<Course> page = courseService.list(SearchCriteria.from(params));
        return ResponseEntity.ok(ApiResponse.ok(page,
                page.getTotalElements() + " course(s) found"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Course>>> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        List<Course> results = courseService.search(keyword, limit);
        return ResponseEntity.ok(ApiResponse.ok(results, results.size() + " match(es)"));
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> options() {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("departments", courseService.departments()), "Filter options"));
    }

    /** Courses still waiting for a teacher (report widget). */
    @GetMapping("/unassigned")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<List<Course>>> unassigned() {
        List<Course> results = courseService.findUnassigned();
        return ResponseEntity.ok(ApiResponse.ok(results,
                results.size() + " course(s) without an assigned faculty member"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Course>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(courseService.getById(id), "Course found"));
    }

    @PostMapping
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Course>> create(@RequestBody Course course) {
        Course saved = courseService.create(course);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(saved, "Course added successfully."));
    }

    @PutMapping("/{id}")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Course>> update(
            @PathVariable Long id, @RequestBody Course course) {
        Course saved = courseService.update(id, course);
        return ResponseEntity.ok(ApiResponse.ok(saved, "Course updated successfully."));
    }

    /** Assign (or with {@code facultyId=null}, remove) the teacher of a course. */
    @PutMapping("/{id}/faculty")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Course>> assignFaculty(
            @PathVariable Long id,
            @RequestParam(required = false) Long facultyId) {
        Course updated = courseService.assignFaculty(id, facultyId);
        String message = facultyId == null
                ? "Faculty cleared from " + updated.getCourseCode()
                : "Faculty assigned to " + updated.getCourseCode();
        return ResponseEntity.ok(ApiResponse.ok(updated, message));
    }

    @DeleteMapping("/{id}")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        courseService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Course deleted successfully."));
    }
}
