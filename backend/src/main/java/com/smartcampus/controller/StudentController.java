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
import com.smartcampus.model.Student;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.StudentService;

/**
 * REST API of the student module.
 *
 * <pre>
 * GET    /api/students          list + search + filter + sort + page
 * GET    /api/students/search   keyword search (type-ahead)
 * GET    /api/students/me       own profile
 * GET    /api/students/options  departments for the filter drop-down
 * GET    /api/students/{id}     one record
 * POST   /api/students          create                201
 * PUT    /api/students/{id}     update                200
 * DELETE /api/students/{id}     delete                200
 * </pre>
 *
 * <p>Role rules are declared with {@link RequireRoles} and enforced by the
 * security filter <b>before</b> this class is ever entered.  The browser cannot
 * bypass them.</p>
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    // ------------------------------------------------------------------
    //  Read
    // ------------------------------------------------------------------

    /** Paged, filtered, sorted list. Students may not read the whole register. */
    @GetMapping
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<PagedResponse<Student>>> list(
            @RequestParam Map<String, String> params) {
        PagedResponse<Student> page = studentService.list(SearchCriteria.from(params));
        return ResponseEntity.ok(ApiResponse.ok(page,
                page.getTotalElements() + " student(s) found"));
    }

    /** Plain keyword search used by the search box and by other modules. */
    @GetMapping("/search")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<List<Student>>> search(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        List<Student> results = studentService.search(keyword, limit);
        return ResponseEntity.ok(ApiResponse.ok(results, results.size() + " match(es)"));
    }

    /** The signed-in student's own record (also reachable by admins/faculty). */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Student>> myProfile() {
        return ResponseEntity.ok(ApiResponse.ok(studentService.getOwnProfile(), "Your profile"));
    }

    /** Filter drop-down values (distinct departments). */
    @GetMapping("/options")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> options() {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("departments", studentService.departments()), "Filter options"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Student>> get(@PathVariable Long id) {
        // ownership rules for students are applied inside the service
        Student student = studentService.getVisible(id);
        return ResponseEntity.ok(ApiResponse.ok(student, "Student found"));
    }

    // ------------------------------------------------------------------
    //  Write
    // ------------------------------------------------------------------

    @PostMapping
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Student>> create(@RequestBody Student student) {
        Student saved = studentService.create(student);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(saved, "Student added successfully."));
    }

    @PutMapping("/{id}")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Student>> update(
            @PathVariable Long id, @RequestBody Student student) {
        Student saved = studentService.update(id, student);
        return ResponseEntity.ok(ApiResponse.ok(saved, "Student updated successfully."));
    }

    @DeleteMapping("/{id}")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        studentService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Student deleted successfully."));
    }
}
