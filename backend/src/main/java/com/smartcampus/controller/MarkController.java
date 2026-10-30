package com.smartcampus.controller;

import java.util.List;

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
import com.smartcampus.dto.AssessmentMarksSheet;
import com.smartcampus.dto.MarksSaveRequest;
import com.smartcampus.dto.MyMarks;
import com.smartcampus.model.Assessment;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.MarkService;

/** Assessment catalogue, faculty marks entry and the student marks view. */
@RestController
@RequestMapping("/api/marks")
public class MarkController {

    private final MarkService markService;

    public MarkController(MarkService markService) {
        this.markService = markService;
    }

    @GetMapping("/assessments")
    @RequireRoles({Role.ADMIN, Role.FACULTY, Role.STUDENT})
    public ResponseEntity<ApiResponse<List<Assessment>>> assessments(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String term) {
        List<Assessment> assessments = markService.listAssessments(courseId, term);
        return ResponseEntity.ok(ApiResponse.ok(
                assessments, assessments.size() + " assessment(s)"));
    }

    @PostMapping("/assessments")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Assessment>> create(
            @RequestBody Assessment assessment) {
        Assessment saved = markService.createAssessment(assessment);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(saved, "Assessment created."));
    }

    @PutMapping("/assessments/{id}")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Assessment>> update(
            @PathVariable Long id, @RequestBody Assessment assessment) {
        return ResponseEntity.ok(ApiResponse.ok(
                markService.updateAssessment(id, assessment), "Assessment updated."));
    }

    @PutMapping("/assessments/{id}/publish")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Assessment>> publish(
            @PathVariable Long id, @RequestParam boolean published) {
        Assessment saved = markService.setAssessmentPublished(id, published);
        return ResponseEntity.ok(ApiResponse.ok(saved,
                published ? "Assessment published to students." : "Assessment moved back to draft."));
    }

    @DeleteMapping("/assessments/{id}")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        markService.deleteAssessment(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Assessment deleted."));
    }

    @GetMapping("/assessments/{id}/sheet")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<AssessmentMarksSheet>> sheet(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                markService.marksSheet(id), "Marks sheet"));
    }

    @PutMapping("/assessments/{id}/marks")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<AssessmentMarksSheet>> saveMarks(
            @PathVariable Long id, @RequestBody MarksSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                markService.saveMarks(id, request), "Marks saved."));
    }

    @GetMapping("/my")
    @RequireRoles(Role.STUDENT)
    public ResponseEntity<ApiResponse<MyMarks>> myMarks() {
        return ResponseEntity.ok(ApiResponse.ok(
                markService.myMarks(), "Your published marks"));
    }
}
