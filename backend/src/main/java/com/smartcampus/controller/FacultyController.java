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
import com.smartcampus.model.Faculty;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.FacultyService;

/**
 * REST API of the faculty module.
 *
 * <pre>
 * GET    /api/faculty          list + search + filter + sort + page
 * GET    /api/faculty/search   keyword search
 * GET    /api/faculty/options  departments / designations for the filters
 * GET    /api/faculty/{id}     one record
 * POST   /api/faculty          create   201
 * PUT    /api/faculty/{id}     update   200
 * DELETE /api/faculty/{id}     delete   200
 * </pre>
 */
@RestController
@RequestMapping("/api/faculty")
public class FacultyController {

    private final FacultyService facultyService;

    public FacultyController(FacultyService facultyService) {
        this.facultyService = facultyService;
    }

    @GetMapping
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<PagedResponse<Faculty>>> list(
            @RequestParam Map<String, String> params) {
        PagedResponse<Faculty> page = facultyService.list(SearchCriteria.from(params));
        return ResponseEntity.ok(ApiResponse.ok(page,
                page.getTotalElements() + " faculty member(s) found"));
    }

    @GetMapping("/search")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<List<Faculty>>> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        List<Faculty> results = facultyService.search(keyword, limit);
        return ResponseEntity.ok(ApiResponse.ok(results, results.size() + " match(es)"));
    }

    @GetMapping("/options")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> options() {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("departments", facultyService.departments(),
                       "designations", facultyService.designations()),
                "Filter options"));
    }

    @GetMapping("/{id}")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Faculty>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(facultyService.getById(id), "Faculty found"));
    }

    @PostMapping
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Faculty>> create(@RequestBody Faculty faculty) {
        Faculty saved = facultyService.create(faculty);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(saved, "Faculty member added successfully."));
    }

    @PutMapping("/{id}")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Faculty>> update(
            @PathVariable Long id, @RequestBody Faculty faculty) {
        Faculty saved = facultyService.update(id, faculty);
        return ResponseEntity.ok(ApiResponse.ok(saved, "Faculty member updated successfully."));
    }

    @DeleteMapping("/{id}")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        facultyService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Faculty member deleted successfully."));
    }
}
