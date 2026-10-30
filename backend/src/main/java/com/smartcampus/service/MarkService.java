package com.smartcampus.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.AssessmentMarksSheet;
import com.smartcampus.dto.CourseMarkSummary;
import com.smartcampus.dto.MarkEntry;
import com.smartcampus.dto.MarksSaveRequest;
import com.smartcampus.dto.MyMarks;
import com.smartcampus.exception.AcademicNotFoundException;
import com.smartcampus.exception.DuplicateRecordException;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.model.Assessment;
import com.smartcampus.model.Course;
import com.smartcampus.model.Student;
import com.smartcampus.model.StudentMark;
import com.smartcampus.model.enums.AssessmentType;
import com.smartcampus.model.enums.Role;
import com.smartcampus.repository.AssessmentRepository;
import com.smartcampus.repository.CourseRepository;
import com.smartcampus.repository.MarkRepository;
import com.smartcampus.utility.Validators;

/** Assessment and marks business rules. */
@Service
public class MarkService {

    private final AcademicAccessService access;
    private final AssessmentRepository assessmentRepository;
    private final MarkRepository markRepository;
    private final CourseRepository courseRepository;
    private final AuditLogService auditLog;

    public MarkService(AcademicAccessService access,
                       AssessmentRepository assessmentRepository,
                       MarkRepository markRepository,
                       CourseRepository courseRepository,
                       AuditLogService auditLog) {
        this.access = access;
        this.assessmentRepository = assessmentRepository;
        this.markRepository = markRepository;
        this.courseRepository = courseRepository;
        this.auditLog = auditLog;
    }

    // ------------------------------------------------------------------
    //  Assessment catalogue
    // ------------------------------------------------------------------

    public List<Assessment> listAssessments(Long courseId, String term) {
        boolean studentView = access.currentUser().role() == Role.STUDENT;
        List<Assessment> assessments;
        if (courseId != null) {
            Course course = access.requireViewableCourse(courseId);
            assessments = assessmentRepository.findByCourse(course.getId(), studentView);
        } else {
            List<Long> courseIds = access.visibleCourses().stream()
                    .map(Course::getId)
                    .toList();
            assessments = assessmentRepository.findByCourseIds(courseIds, studentView);
        }
        if (term != null && !term.isBlank()) {
            assessments = assessments.stream()
                    .filter(assessment -> assessment.getTerm() != null
                            && assessment.getTerm().equalsIgnoreCase(term.trim()))
                    .toList();
        }
        return assessments;
    }

    public Assessment createAssessment(Assessment input) {
        Course course = access.requireManagedCourse(input.getCourseId());
        validateAssessment(input);
        if (assessmentRepository.existsByCourseTitleTerm(
                course.getId(), input.getTitle(), input.getTerm(), null)) {
            throw new DuplicateRecordException(
                    "An assessment with this title already exists in the selected term.");
        }
        input.setId(null);
        input.setCourseId(course.getId());
        input.setCreatedByFacultyId(access.currentFacultyIdOrNull());
        Assessment saved = assessmentRepository.save(input);
        auditLog.record("CREATE", "ASSESSMENT",
                "Created assessment " + saved.getTitle() + " for " + course.getCourseCode() + ".");
        return assessmentRepository.findById(saved.getId()).orElse(saved);
    }

    public Assessment updateAssessment(Long id, Assessment input) {
        Assessment existing = requireAssessment(id);
        access.requireManagedCourse(existing.getCourseId());
        validateAssessment(input);
        if (assessmentRepository.existsByCourseTitleTerm(
                existing.getCourseId(), input.getTitle(), input.getTerm(), id)) {
            throw new DuplicateRecordException(
                    "An assessment with this title already exists in the selected term.");
        }
        input.setId(id);
        input.setCourseId(existing.getCourseId());
        input.setCreatedByFacultyId(existing.getCreatedByFacultyId());
        assessmentRepository.update(input);
        auditLog.record("UPDATE", "ASSESSMENT",
                "Updated assessment " + input.getTitle() + ".");
        return assessmentRepository.findById(id).orElse(input);
    }

    public Assessment setAssessmentPublished(Long id, boolean published) {
        Assessment existing = requireAssessment(id);
        access.requireManagedCourse(existing.getCourseId());
        assessmentRepository.setPublished(id, published);
        auditLog.record("UPDATE", "ASSESSMENT",
                (published ? "Published" : "Unpublished") + " assessment " + existing.getTitle() + ".");
        return requireAssessment(id);
    }

    public void deleteAssessment(Long id) {
        Assessment existing = requireAssessment(id);
        access.requireManagedCourse(existing.getCourseId());
        assessmentRepository.deleteById(id);
        auditLog.record("DELETE", "ASSESSMENT",
                "Deleted assessment " + existing.getTitle() + ".");
    }

    // ------------------------------------------------------------------
    //  Faculty marks sheet
    // ------------------------------------------------------------------

    public AssessmentMarksSheet marksSheet(Long assessmentId) {
        Assessment assessment = requireAssessment(assessmentId);
        Course course = access.requireManagedCourse(assessment.getCourseId());
        List<Student> students = access.enrolledStudents(course);
        Map<Long, StudentMark> existing = markRepository.findByAssessment(assessmentId).stream()
                .collect(Collectors.toMap(StudentMark::getStudentId, Function.identity()));
        List<StudentMark> rows = students.stream()
                .map(student -> markView(assessment, student, existing.get(student.getId())))
                .toList();
        return new AssessmentMarksSheet(assessment, rows);
    }

    public AssessmentMarksSheet saveMarks(Long assessmentId, MarksSaveRequest request) {
        Assessment assessment = requireAssessment(assessmentId);
        Course course = access.requireManagedCourse(assessment.getCourseId());
        if (request == null || request.marks() == null || request.marks().isEmpty()) {
            throw new InvalidInputException("At least one mark entry is required.");
        }

        Map<Long, Student> enrolled = access.enrolledStudents(course).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        Long facultyId = access.currentFacultyIdOrNull();

        for (MarkEntry entry : request.marks()) {
            if (entry.studentId() == null) {
                throw new InvalidInputException("Each mark entry must include a student.");
            }
            Student student = enrolled.get(entry.studentId());
            if (student == null) {
                throw new InvalidInputException(
                        "A mark was submitted for a student who is not enrolled in this subject.");
            }
            if (entry.score() != null) {
                if (entry.score().compareTo(BigDecimal.ZERO) < 0
                        || entry.score().compareTo(assessment.getMaxScore()) > 0) {
                    throw new InvalidInputException(
                            "Score for " + student.getName() + " must be between 0 and "
                                    + assessment.getMaxScore().stripTrailingZeros().toPlainString() + ".");
                }
            }
            String remarks = entry.remarks() == null ? null : entry.remarks().trim();
            if (remarks != null && remarks.length() > 255) {
                throw new InvalidInputException("Remarks must be 255 characters or fewer.");
            }
            markRepository.upsert(assessmentId, student.getId(), entry.score(), remarks, facultyId);
        }

        auditLog.record("UPDATE", "MARKS",
                "Saved marks for assessment " + assessment.getTitle()
                        + " (" + course.getCourseCode() + ").");
        return marksSheet(assessmentId);
    }

    // ------------------------------------------------------------------
    //  Student view
    // ------------------------------------------------------------------

    public MyMarks myMarks() {
        Student student = access.requireCurrentStudent();
        List<Course> courses = courseRepository.findActiveByDepartmentAndSemester(
                student.getDepartment(), student.getSemester());
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        List<Assessment> assessments = assessmentRepository.findByCourseIds(courseIds, true);
        Map<Long, StudentMark> existing = markRepository.findByStudent(student.getId(), true).stream()
                .collect(Collectors.toMap(StudentMark::getAssessmentId, Function.identity()));

        Map<Long, List<StudentMark>> byCourse = new LinkedHashMap<>();
        List<StudentMark> allMarks = new ArrayList<>();
        for (Assessment assessment : assessments) {
            StudentMark row = markView(assessment, student, existing.get(assessment.getId()));
            allMarks.add(row);
            byCourse.computeIfAbsent(assessment.getCourseId(), key -> new ArrayList<>()).add(row);
        }

        List<CourseMarkSummary> courseSummaries = new ArrayList<>();
        BigDecimal totalEarned = BigDecimal.ZERO;
        BigDecimal totalMax = BigDecimal.ZERO;
        int totalAssessments = 0;

        for (Course course : courses) {
            List<StudentMark> rows = byCourse.getOrDefault(course.getId(), List.of());
            BigDecimal earned = BigDecimal.ZERO;
            BigDecimal max = BigDecimal.ZERO;
            int counted = 0;
            for (StudentMark row : rows) {
                if (row.getScore() != null) {
                    earned = earned.add(row.getScore());
                    max = max.add(row.getMaxScore() == null ? BigDecimal.ZERO : row.getMaxScore());
                    counted++;
                }
            }
            BigDecimal percentage = max.compareTo(BigDecimal.ZERO) == 0
                    ? null
                    : earned.multiply(new BigDecimal("100"))
                            .divide(max, 2, RoundingMode.HALF_UP);
            courseSummaries.add(new CourseMarkSummary(
                    course, rows, earned, max, percentage, counted));
            totalEarned = totalEarned.add(earned);
            totalMax = totalMax.add(max);
            totalAssessments += counted;
        }

        BigDecimal overallPercentage = totalMax.compareTo(BigDecimal.ZERO) == 0
                ? null
                : totalEarned.multiply(new BigDecimal("100"))
                        .divide(totalMax, 2, RoundingMode.HALF_UP);

        return new MyMarks(allMarks, courseSummaries, totalEarned, totalMax,
                overallPercentage, totalAssessments);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    public Assessment requireAssessment(Long id) {
        return assessmentRepository.findById(id)
                .orElseThrow(() -> new AcademicNotFoundException("Assessment", id));
    }

    private StudentMark markView(Assessment assessment, Student student, StudentMark existing) {
        StudentMark row = existing == null ? new StudentMark() : existing;
        row.setAssessmentId(assessment.getId());
        row.setStudentId(student.getId());
        row.setStudentName(student.getName());
        row.setStudentUsn(student.getUsn());
        row.setSection(student.getSection());
        row.setCourseId(assessment.getCourseId());
        row.setCourseCode(assessment.getCourseCode());
        row.setCourseName(assessment.getCourseName());
        row.setSemester(assessment.getSemester());
        row.setTitle(assessment.getTitle());
        row.setAssessmentType(assessment.getAssessmentType());
        row.setTerm(assessment.getTerm());
        row.setMaxScore(assessment.getMaxScore());
        row.setAssessmentDate(assessment.getAssessmentDate());
        row.setPublished(assessment.isPublished());
        if (existing == null) {
            row.setScore(null);
        }
        return row;
    }

    private void validateAssessment(Assessment input) {
        if (input == null) {
            throw new InvalidInputException("Assessment details are required.");
        }
        input.setTitle(Validators.requiredText(input.getTitle(), "Assessment title", 150));
        input.setTerm(Validators.requiredText(input.getTerm(), "Term", 30));
        if (input.getAssessmentType() == null) {
            input.setAssessmentType(AssessmentType.OTHER);
        }
        if (input.getMaxScore() == null
                || input.getMaxScore().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException("Maximum score must be greater than zero.");
        }
        if (input.getWeight() != null
                && (input.getWeight().compareTo(BigDecimal.ZERO) < 0
                    || input.getWeight().compareTo(new BigDecimal("100")) > 0)) {
            throw new InvalidInputException("Weight must be between 0 and 100.");
        }
    }
}
