package com.smartcampus.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartcampus.dto.AcademicProfile;
import com.smartcampus.dto.AcademicStudentSummary;
import com.smartcampus.dto.ApiResponse;
import com.smartcampus.dto.EligibilityResult;
import com.smartcampus.dto.FacultySummary;
import com.smartcampus.dto.MentorAssignmentRequest;
import com.smartcampus.dto.StudentSummary;
import com.smartcampus.model.AcademicSettings;
import com.smartcampus.model.Course;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.AcademicAccessService;
import com.smartcampus.service.AcademicProfileService;
import com.smartcampus.service.AcademicSettingsService;

/**
 * Shared academic read/configuration endpoints.
 *
 * <p>All authorization is performed in the services; the annotations below are
 * an additional server-side gate for the whole role group.</p>
 */
@RestController
@RequestMapping("/api/academics")
public class AcademicController {

    private final AcademicAccessService access;
    private final AcademicProfileService profileService;
    private final AcademicSettingsService settingsService;

    public AcademicController(AcademicAccessService access,
                              AcademicProfileService profileService,
                              AcademicSettingsService settingsService) {
        this.access = access;
        this.profileService = profileService;
        this.settingsService = settingsService;
    }

    @GetMapping("/courses")
    @RequireRoles({Role.ADMIN, Role.FACULTY, Role.STUDENT})
    public ResponseEntity<ApiResponse<List<Course>>> courses() {
        List<Course> courses = access.visibleCourses();
        return ResponseEntity.ok(ApiResponse.ok(courses, courses.size() + " subject(s)"));
    }

    @GetMapping("/courses/{courseId}/students")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<List<StudentSummary>>> courseStudents(
            @PathVariable Long courseId) {
        List<StudentSummary> students = access.enrolledStudents(
                        access.requireManagedCourse(courseId))
                .stream()
                .map(StudentSummary::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(students, students.size() + " enrolled student(s)"));
    }

    @GetMapping("/students")
    @RequireRoles({Role.ADMIN, Role.FACULTY, Role.STUDENT})
    public ResponseEntity<ApiResponse<List<AcademicStudentSummary>>> students(
            @RequestParam(required = false) Long courseId) {
        List<AcademicStudentSummary> students = profileService.listStudents(courseId);
        return ResponseEntity.ok(ApiResponse.ok(students, students.size() + " student(s)"));
    }

    @GetMapping("/students/{studentId}/profile")
    @RequireRoles({Role.ADMIN, Role.FACULTY, Role.STUDENT})
    public ResponseEntity<ApiResponse<AcademicProfile>> profile(@PathVariable Long studentId) {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.profile(studentId), "Academic profile"));
    }

    @GetMapping("/profile/me")
    @RequireRoles(Role.STUDENT)
    public ResponseEntity<ApiResponse<AcademicProfile>> myProfile() {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.profileForCurrentStudent(), "Your academic profile"));
    }

    @GetMapping("/eligibility/me")
    @RequireRoles(Role.STUDENT)
    public ResponseEntity<ApiResponse<EligibilityResult>> myEligibility() {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.eligibilityForCurrentStudent(), "Your eligibility"));
    }

    @GetMapping("/eligibility/students")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<List<AcademicStudentSummary>>> eligibilityStudents(
            @RequestParam(required = false) Long courseId) {
        List<AcademicStudentSummary> students = profileService.eligibilityStudents(courseId);
        return ResponseEntity.ok(ApiResponse.ok(students, students.size() + " student(s)"));
    }

    @GetMapping("/settings")
    @RequireRoles({Role.ADMIN, Role.FACULTY, Role.STUDENT})
    public ResponseEntity<ApiResponse<AcademicSettings>> settings() {
        return ResponseEntity.ok(ApiResponse.ok(settingsService.get(), "Eligibility rules"));
    }

    @PutMapping("/settings")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<AcademicSettings>> updateSettings(
            @RequestBody AcademicSettings settings) {
        AcademicSettings saved = settingsService.update(settings);
        return ResponseEntity.ok(ApiResponse.ok(saved, "Eligibility rules saved."));
    }

    @PutMapping("/students/{studentId}/mentors")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<StudentSummary>> assignMentors(
            @PathVariable Long studentId,
            @RequestBody MentorAssignmentRequest request) {
        StudentSummary saved = profileService.assignMentors(studentId, request);
        return ResponseEntity.ok(ApiResponse.ok(
                saved, "Class teacher and mentor updated."));
    }

    @GetMapping("/faculty-options")
    @RequireRoles(Role.ADMIN)
    public ResponseEntity<ApiResponse<List<FacultySummary>>> facultyOptions() {
        List<FacultySummary> faculty = profileService.facultyOptions();
        return ResponseEntity.ok(ApiResponse.ok(faculty, faculty.size() + " faculty member(s)"));
    }
}
