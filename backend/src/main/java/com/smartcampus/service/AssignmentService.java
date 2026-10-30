package com.smartcampus.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartcampus.dto.AssignmentReviewRequest;
import com.smartcampus.exception.AcademicNotFoundException;
import com.smartcampus.exception.DuplicateRecordException;
import com.smartcampus.exception.ForbiddenException;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.model.Assignment;
import com.smartcampus.model.AssignmentSubmission;
import com.smartcampus.model.Course;
import com.smartcampus.model.Student;
import com.smartcampus.model.enums.Role;
import com.smartcampus.repository.AssignmentRepository;
import com.smartcampus.repository.CourseRepository;
import com.smartcampus.repository.SubmissionRepository;
import com.smartcampus.utility.Validators;

/** Assignment lifecycle, secure submission and faculty review. */
@Service
public class AssignmentService {

    private final AcademicAccessService access;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseRepository courseRepository;
    private final FileStorageService fileStorage;
    private final AuditLogService auditLog;

    public AssignmentService(AcademicAccessService access,
                             AssignmentRepository assignmentRepository,
                             SubmissionRepository submissionRepository,
                             CourseRepository courseRepository,
                             FileStorageService fileStorage,
                             AuditLogService auditLog) {
        this.access = access;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.courseRepository = courseRepository;
        this.fileStorage = fileStorage;
        this.auditLog = auditLog;
    }

    // ------------------------------------------------------------------
    //  Assignment catalogue
    // ------------------------------------------------------------------

    public List<Assignment> listAssignments(Long courseId) {
        boolean studentView = access.currentUser().role() == Role.STUDENT;
        List<Assignment> assignments;
        if (courseId != null) {
            Course course = studentView
                    ? access.requireViewableCourse(courseId)
                    : access.requireManagedCourse(courseId);
            assignments = assignmentRepository.findByCourse(course.getId(), studentView);
        } else {
            List<Long> courseIds = access.visibleCourses().stream()
                    .map(Course::getId)
                    .toList();
            assignments = assignmentRepository.findByCourseIds(courseIds, studentView);
        }
        for (Assignment assignment : assignments) {
            decorate(assignment, studentView);
        }
        return assignments;
    }

    public Assignment getAssignment(Long id) {
        Assignment assignment = requireAssignment(id);
        boolean studentView = access.currentUser().role() == Role.STUDENT;
        if (studentView) {
            if (!assignment.isPublished()) {
                throw new AcademicNotFoundException("Assignment", id);
            }
            Course course = access.requireCourse(assignment.getCourseId());
            access.assertStudentEnrolled(course, access.requireCurrentStudent());
        } else {
            access.requireManagedCourse(assignment.getCourseId());
        }
        decorate(assignment, studentView);
        return assignment;
    }

    public Assignment createAssignment(Assignment input) {
        if (input.getCourseId() == null) {
            throw new InvalidInputException("A subject must be selected.");
        }
        Course course = access.requireManagedCourse(input.getCourseId());
        validateAssignment(input);
        if (assignmentRepository.existsByCourseTitle(course.getId(), input.getTitle(), null)) {
            throw new DuplicateRecordException(
                    "An assignment with this title already exists for the selected subject.");
        }
        input.setId(null);
        input.setCourseId(course.getId());
        input.setCreatedByFacultyId(access.currentFacultyIdOrNull());
        Assignment saved = assignmentRepository.save(input);
        auditLog.record("CREATE", "ASSIGNMENT",
                "Created assignment " + saved.getTitle() + " for " + course.getCourseCode() + ".");
        return assignmentRepository.findById(saved.getId()).orElse(saved);
    }

    public Assignment updateAssignment(Long id, Assignment input) {
        Assignment existing = requireAssignment(id);
        access.requireManagedCourse(existing.getCourseId());
        validateAssignment(input);
        if (assignmentRepository.existsByCourseTitle(
                existing.getCourseId(), input.getTitle(), id)) {
            throw new DuplicateRecordException(
                    "An assignment with this title already exists for the selected subject.");
        }
        input.setId(id);
        input.setCourseId(existing.getCourseId());
        input.setCreatedByFacultyId(existing.getCreatedByFacultyId());
        assignmentRepository.update(input);
        auditLog.record("UPDATE", "ASSIGNMENT",
                "Updated assignment " + input.getTitle() + ".");
        return assignmentRepository.findById(id).orElse(input);
    }

    public Assignment setPublished(Long id, boolean published) {
        Assignment existing = requireAssignment(id);
        access.requireManagedCourse(existing.getCourseId());
        assignmentRepository.setPublished(id, published);
        auditLog.record("UPDATE", "ASSIGNMENT",
                (published ? "Published" : "Unpublished") + " assignment " + existing.getTitle() + ".");
        return requireAssignment(id);
    }

    public void deleteAssignment(Long id) {
        Assignment existing = requireAssignment(id);
        access.requireManagedCourse(existing.getCourseId());
        List<AssignmentSubmission> submissions = submissionRepository.findByAssignment(id);
        for (AssignmentSubmission submission : submissions) {
            fileStorage.delete(submission.getStoredFileName());
        }
        assignmentRepository.deleteById(id);
        auditLog.record("DELETE", "ASSIGNMENT",
                "Deleted assignment " + existing.getTitle() + " and its submissions.");
    }

    // ------------------------------------------------------------------
    //  Student submission
    // ------------------------------------------------------------------

    public AssignmentSubmission submit(Long assignmentId, MultipartFile file) {
        Student student = access.requireCurrentStudent();
        Assignment assignment = requireAssignment(assignmentId);
        if (!assignment.isPublished()) {
            throw new AcademicNotFoundException("Assignment", assignmentId);
        }
        Course course = access.requireCourse(assignment.getCourseId());
        access.assertStudentEnrolled(course, student);

        AssignmentSubmission existing = submissionRepository
                .findByAssignmentAndStudent(assignmentId, student.getId()).orElse(null);
        if (existing != null && !assignment.isAllowResubmission()) {
            throw new InvalidInputException(
                    "Resubmission is not allowed for this assignment.");
        }

        boolean late = LocalDateTime.now().isAfter(assignment.getDueAt());
        if (late && !assignment.isAllowLate()) {
            throw new InvalidInputException(
                    "The due date has passed and late submissions are not allowed.");
        }

        Set<String> allowed = fileStorage.parseTypes(assignment.getAllowedFileTypes());
        long maxBytes = Math.min(
                (long) assignment.getMaxFileSizeMb() * 1024L * 1024L,
                fileStorage.globalMaxSizeBytes());
        FileStorageService.StoredFile stored = fileStorage.store(file, allowed, maxBytes);

        try {
            AssignmentSubmission saved = submissionRepository.saveOrReplace(
                    assignmentId, student.getId(),
                    stored.originalFileName(), stored.storedFileName(),
                    stored.contentType(), stored.size(), late);
            auditLog.record("SUBMIT", "ASSIGNMENT",
                    "Submitted assignment " + assignment.getTitle() + ".");
            return saved;
        } catch (RuntimeException ex) {
            fileStorage.delete(stored.storedFileName());
            throw ex;
        }
    }

    // ------------------------------------------------------------------
    //  Faculty review
    // ------------------------------------------------------------------

    public List<AssignmentSubmission> submissions(Long assignmentId) {
        Assignment assignment = requireAssignment(assignmentId);
        access.requireManagedCourse(assignment.getCourseId());
        return submissionRepository.findByAssignment(assignmentId);
    }

    public AssignmentSubmission review(Long assignmentId, Long submissionId,
                                       AssignmentReviewRequest request) {
        Assignment assignment = requireAssignment(assignmentId);
        access.requireManagedCourse(assignment.getCourseId());
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new AcademicNotFoundException("Submission", submissionId));
        if (!Objects.equals(submission.getAssignmentId(), assignmentId)) {
            throw new AcademicNotFoundException("Submission", submissionId);
        }

        BigDecimal grade = request == null ? null : request.grade();
        String feedback = request == null ? null : request.feedback();
        if (grade != null) {
            if (grade.compareTo(BigDecimal.ZERO) < 0
                    || grade.compareTo(assignment.getMaxScore()) > 0) {
                throw new InvalidInputException(
                        "Grade must be between 0 and "
                                + assignment.getMaxScore().stripTrailingZeros().toPlainString() + ".");
            }
        }
        if (feedback != null) {
            feedback = feedback.trim();
            if (feedback.length() > 1000) {
                throw new InvalidInputException("Feedback must be 1000 characters or fewer.");
            }
        }

        submissionRepository.review(submissionId, grade, feedback,
                access.currentFacultyIdOrNull());
        auditLog.record("REVIEW", "ASSIGNMENT",
                "Reviewed submission for assignment " + assignment.getTitle() + ".");
        return submissionRepository.findById(submissionId).orElseThrow();
    }

    public SubmissionFile download(Long submissionId) {
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new AcademicNotFoundException("Submission", submissionId));

        if (access.currentUser().role() == Role.STUDENT) {
            Student student = access.requireCurrentStudent();
            if (!Objects.equals(student.getId(), submission.getStudentId())) {
                throw new ForbiddenException("You may only download your own submission.");
            }
        } else {
            access.requireManagedCourse(submission.getCourseId());
        }

        Path path = fileStorage.load(submission.getStoredFileName());
        return new SubmissionFile(submission.getOriginalFileName(),
                submission.getContentType(), path);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    public Assignment requireAssignment(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new AcademicNotFoundException("Assignment", id));
    }

    private void decorate(Assignment assignment, boolean studentView) {
        if (studentView) {
            Student student = access.requireCurrentStudent();
            assignment.setMySubmission(submissionRepository
                    .findByAssignmentAndStudent(assignment.getId(), student.getId())
                    .orElse(null));
        } else {
            assignment.setSubmissionCount(
                    submissionRepository.countByAssignment(assignment.getId()));
            assignment.setGradedCount(
                    submissionRepository.countGradedByAssignment(assignment.getId()));
        }
    }

    private void validateAssignment(Assignment input) {
        if (input == null) {
            throw new InvalidInputException("Assignment details are required.");
        }
        input.setTitle(Validators.requiredText(input.getTitle(), "Assignment title", 150));
        input.setInstructions(Validators.requiredText(
                input.getInstructions(), "Instructions", 5000));
        if (input.getDueAt() == null) {
            throw new InvalidInputException("Due date and time are required.");
        }
        if (input.getMaxScore() == null
                || input.getMaxScore().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException("Maximum score must be greater than zero.");
        }

        Set<String> requested = fileStorage.parseTypes(input.getAllowedFileTypes());
        Set<String> global = fileStorage.globalExtensions();
        for (String type : requested) {
            if (!global.contains(type)) {
                throw new InvalidInputException(
                        "File type ." + type + " is not enabled on this server.");
            }
        }
        input.setAllowedFileTypes(String.join(",", requested));

        int maxMb = input.getMaxFileSizeMb();
        if (maxMb < 1 || maxMb > 50) {
            throw new InvalidInputException("Maximum file size must be between 1 and 50 MB.");
        }
        long globalMaxMb = Math.max(1, fileStorage.globalMaxSizeBytes() / (1024L * 1024L));
        if (maxMb > globalMaxMb) {
            throw new InvalidInputException(
                    "Maximum file size cannot exceed the server limit of " + globalMaxMb + " MB.");
        }
    }

    public record SubmissionFile(String originalFileName, String contentType, Path path) {
    }
}
