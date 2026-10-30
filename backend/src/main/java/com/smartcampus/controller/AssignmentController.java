package com.smartcampus.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import com.smartcampus.dto.ApiResponse;
import com.smartcampus.dto.AssignmentReviewRequest;
import com.smartcampus.exception.SmartCampusException;
import com.smartcampus.model.Assignment;
import com.smartcampus.model.AssignmentSubmission;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.AssignmentService;

/** Assignment catalogue, secure student uploads and faculty review. */
@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    @RequireRoles({Role.ADMIN, Role.FACULTY, Role.STUDENT})
    public ResponseEntity<ApiResponse<List<Assignment>>> list(
            @RequestParam(required = false) Long courseId) {
        List<Assignment> assignments = assignmentService.listAssignments(courseId);
        return ResponseEntity.ok(ApiResponse.ok(
                assignments, assignments.size() + " assignment(s)"));
    }

    @GetMapping("/{id}")
    @RequireRoles({Role.ADMIN, Role.FACULTY, Role.STUDENT})
    public ResponseEntity<ApiResponse<Assignment>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                assignmentService.getAssignment(id), "Assignment"));
    }

    @PostMapping
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Assignment>> create(
            @RequestBody Assignment assignment) {
        Assignment saved = assignmentService.createAssignment(assignment);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(saved, "Assignment created."));
    }

    @PutMapping("/{id}")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Assignment>> update(
            @PathVariable Long id, @RequestBody Assignment assignment) {
        return ResponseEntity.ok(ApiResponse.ok(
                assignmentService.updateAssignment(id, assignment), "Assignment updated."));
    }

    @PutMapping("/{id}/publish")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Assignment>> publish(
            @PathVariable Long id, @RequestParam boolean published) {
        Assignment saved = assignmentService.setPublished(id, published);
        return ResponseEntity.ok(ApiResponse.ok(saved,
                published ? "Assignment published to students." : "Assignment moved back to draft."));
    }

    @DeleteMapping("/{id}")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Assignment deleted."));
    }

    @GetMapping("/{id}/submissions")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<List<AssignmentSubmission>>> submissions(
            @PathVariable Long id) {
        List<AssignmentSubmission> submissions = assignmentService.submissions(id);
        return ResponseEntity.ok(ApiResponse.ok(
                submissions, submissions.size() + " submission(s)"));
    }

    @PutMapping("/{id}/submissions/{submissionId}/review")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<AssignmentSubmission>> review(
            @PathVariable Long id,
            @PathVariable Long submissionId,
            @RequestBody AssignmentReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                assignmentService.review(id, submissionId, request),
                "Submission review saved."));
    }

    @PostMapping(value = "/{id}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRoles(Role.STUDENT)
    public ResponseEntity<ApiResponse<AssignmentSubmission>> submit(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
                assignmentService.submit(id, file), "Assignment submitted."));
    }

    @GetMapping("/submissions/{submissionId}/file")
    @RequireRoles({Role.ADMIN, Role.FACULTY, Role.STUDENT})
    public ResponseEntity<Resource> download(@PathVariable Long submissionId) {
        AssignmentService.SubmissionFile file = assignmentService.download(submissionId);
        try {
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (file.contentType() != null && !file.contentType().isBlank()) {
                try {
                    mediaType = MediaType.parseMediaType(file.contentType());
                } catch (IllegalArgumentException ignored) {
                    // Keep the safe generic binary type.
                }
            }
            String fileName = file.originalFileName() == null || file.originalFileName().isBlank()
                    ? "submission" : file.originalFileName();
            String disposition = ContentDisposition.attachment()
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build().toString();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header("X-Content-Type-Options", "nosniff")
                    .contentType(mediaType)
                    .contentLength(Files.size(file.path()))
                    .body(new FileSystemResource(file.path()));
        } catch (IOException ex) {
            throw new SmartCampusException(
                    "The submission file could not be read. Please try again.", ex);
        }
    }
}
