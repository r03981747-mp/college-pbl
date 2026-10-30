package com.smartcampus.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.AcademicProfile;
import com.smartcampus.dto.AcademicStudentSummary;
import com.smartcampus.dto.AttendanceSummary;
import com.smartcampus.dto.CourseAcademic;
import com.smartcampus.dto.EligibilityResult;
import com.smartcampus.dto.FacultySummary;
import com.smartcampus.dto.MentorAssignmentRequest;
import com.smartcampus.dto.StudentSummary;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.model.Assignment;
import com.smartcampus.model.AssignmentSubmission;
import com.smartcampus.model.AttendanceRecord;
import com.smartcampus.model.Course;
import com.smartcampus.model.Faculty;
import com.smartcampus.model.Student;
import com.smartcampus.model.StudentMark;
import com.smartcampus.model.enums.FacultyStatus;
import com.smartcampus.repository.AssignmentRepository;
import com.smartcampus.repository.AttendanceRepository;
import com.smartcampus.repository.CourseRepository;
import com.smartcampus.repository.FacultyRepository;
import com.smartcampus.repository.MarkRepository;
import com.smartcampus.repository.StudentRepository;
import com.smartcampus.repository.SubmissionRepository;
import com.smartcampus.security.AuthContext;

/** Builds student academic profiles and the admin mentor-assignment view. */
@Service
public class AcademicProfileService {

    private final AcademicAccessService access;
    private final AcademicSettingsService settingsService;
    private final EligibilityService eligibilityService;
    private final StudentRepository studentRepository;
    private final FacultyRepository facultyRepository;
    private final CourseRepository courseRepository;
    private final MarkRepository markRepository;
    private final AttendanceRepository attendanceRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AuditLogService auditLog;

    public AcademicProfileService(AcademicAccessService access,
                                  AcademicSettingsService settingsService,
                                  EligibilityService eligibilityService,
                                  StudentRepository studentRepository,
                                  FacultyRepository facultyRepository,
                                  CourseRepository courseRepository,
                                  MarkRepository markRepository,
                                  AttendanceRepository attendanceRepository,
                                  AssignmentRepository assignmentRepository,
                                  SubmissionRepository submissionRepository,
                                  AuditLogService auditLog) {
        this.access = access;
        this.settingsService = settingsService;
        this.eligibilityService = eligibilityService;
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
        this.courseRepository = courseRepository;
        this.markRepository = markRepository;
        this.attendanceRepository = attendanceRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.auditLog = auditLog;
    }

    public AcademicProfile profileForCurrentStudent() {
        return profile(access.requireCurrentStudent().getId());
    }

    public AcademicProfile profile(Long studentId) {
        Student student = access.requireStudent(studentId);
        access.assertCanViewStudent(student);
        boolean staffView = !access.currentUser().role().name().equals("STUDENT");

        List<Course> courses = courseRepository.findActiveByDepartmentAndSemester(
                student.getDepartment(), student.getSemester());
        List<CourseAcademic> courseViews = new ArrayList<>();

        for (Course course : courses) {
            List<StudentMark> marks = markRepository.findByStudentAndCourse(
                    student.getId(), course.getId(), !staffView);
            List<AttendanceRecord> attendance = attendanceRepository.findByStudentAndCourse(
                    student.getId(), course.getId());
            AttendanceSummary attendanceSummary = AttendanceSummary.from(
                    attendance, settingsService.get().isLateCountsAsPresent());

            List<Assignment> assignments = assignmentRepository.findByCourse(
                    course.getId(), !staffView);
            for (Assignment assignment : assignments) {
                if (staffView) {
                    assignment.setSubmissionCount(
                            submissionRepository.countByAssignment(assignment.getId()));
                    assignment.setGradedCount(
                            submissionRepository.countGradedByAssignment(assignment.getId()));
                } else {
                    assignment.setMySubmission(submissionRepository
                            .findByAssignmentAndStudent(assignment.getId(), student.getId())
                            .orElse(null));
                }
            }

            courseViews.add(new CourseAcademic(
                    course, marks, attendanceSummary, attendance, assignments));
        }

        EligibilityResult eligibility = eligibilityService.calculate(student, staffView);
        Faculty classTeacher = student.getClassTeacherId() == null
                ? null
                : facultyRepository.findById(student.getClassTeacherId()).orElse(null);
        Faculty mentor = student.getMentorId() == null
                ? null
                : facultyRepository.findById(student.getMentorId()).orElse(null);

        return new AcademicProfile(
                StudentSummary.from(student),
                courseViews,
                eligibility,
                FacultySummary.from(classTeacher),
                FacultySummary.from(mentor));
    }

    public EligibilityResult eligibilityForCurrentStudent() {
        Student student = access.requireCurrentStudent();
        return eligibilityService.calculate(student, false);
    }

    public List<AcademicStudentSummary> eligibilityStudents(Long courseId) {
        return listStudents(courseId);
    }

    public List<AcademicStudentSummary> listStudents(Long courseId) {
        boolean staffView = !access.currentUser().role().name().equals("STUDENT");
        return access.accessibleStudents(courseId).stream()
                .map(student -> new AcademicStudentSummary(
                        StudentSummary.from(student),
                        eligibilityService.calculate(student, staffView)))
                .toList();
    }

    public List<FacultySummary> facultyOptions() {
        access.currentUser();
        if (!access.isAdmin()) {
            throw new com.smartcampus.exception.ForbiddenException(
                    "Only an administrator can view faculty assignment options.");
        }
        return facultyRepository.findAllActive().stream()
                .map(FacultySummary::from)
                .toList();
    }

    public StudentSummary assignMentors(Long studentId, MentorAssignmentRequest request) {
        access.currentUser();
        if (!access.isAdmin()) {
            throw new com.smartcampus.exception.ForbiddenException(
                    "Only an administrator can assign class teachers or mentors.");
        }
        Student student = access.requireStudent(studentId);
        validateFaculty(request.classTeacherId(), "Class teacher");
        validateFaculty(request.mentorId(), "Mentor");

        studentRepository.assignClassTeacherMentor(
                studentId, request.classTeacherId(), request.mentorId());
        Student updated = access.requireStudent(studentId);
        auditLog.record("UPDATE", "STUDENT_MENTOR",
                "Assigned class teacher/mentor for " + updated.getUsn()
                        + " (class teacher: " + updated.getClassTeacherName()
                        + ", mentor: " + updated.getMentorName() + ").");
        return StudentSummary.from(updated);
    }

    private void validateFaculty(Long facultyId, String label) {
        if (facultyId == null) {
            return;
        }
        Faculty faculty = facultyRepository.findById(facultyId)
                .orElseThrow(() -> new InvalidInputException(
                        label + ": faculty member was not found."));
        if (faculty.getStatus() != FacultyStatus.ACTIVE) {
            throw new InvalidInputException(label + ": faculty member is not active.");
        }
    }
}
