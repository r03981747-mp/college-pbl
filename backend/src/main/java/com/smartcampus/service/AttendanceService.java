package com.smartcampus.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.AttendanceEntry;
import com.smartcampus.dto.AttendanceRow;
import com.smartcampus.dto.AttendanceSaveRequest;
import com.smartcampus.dto.AttendanceSheet;
import com.smartcampus.dto.AttendanceSummary;
import com.smartcampus.dto.CourseAttendance;
import com.smartcampus.dto.MyAttendance;
import com.smartcampus.dto.StudentSummary;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.model.AttendanceRecord;
import com.smartcampus.model.Course;
import com.smartcampus.model.Student;
import com.smartcampus.model.enums.AttendanceStatus;
import com.smartcampus.repository.AttendanceRepository;
import com.smartcampus.repository.CourseRepository;

/** Attendance register rules and student self-view. */
@Service
public class AttendanceService {

    private final AcademicAccessService access;
    private final AcademicSettingsService settingsService;
    private final AttendanceRepository attendanceRepository;
    private final CourseRepository courseRepository;
    private final AuditLogService auditLog;

    public AttendanceService(AcademicAccessService access,
                             AcademicSettingsService settingsService,
                             AttendanceRepository attendanceRepository,
                             CourseRepository courseRepository,
                             AuditLogService auditLog) {
        this.access = access;
        this.settingsService = settingsService;
        this.attendanceRepository = attendanceRepository;
        this.courseRepository = courseRepository;
        this.auditLog = auditLog;
    }

    public AttendanceSheet sheet(Long courseId, LocalDate date) {
        Course course = access.requireManagedCourse(courseId);
        LocalDate day = requireDate(date);
        return buildSheet(course, day);
    }

    public AttendanceSheet save(AttendanceSaveRequest request) {
        if (request == null || request.courseId() == null) {
            throw new InvalidInputException("A subject must be selected.");
        }
        Course course = access.requireManagedCourse(request.courseId());
        LocalDate day = requireDate(request.date());
        if (request.records() == null || request.records().isEmpty()) {
            throw new InvalidInputException("At least one attendance row is required.");
        }

        Map<Long, Student> enrolled = access.enrolledStudents(course).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        Long facultyId = access.currentFacultyIdOrNull();

        for (AttendanceEntry entry : request.records()) {
            if (entry.studentId() == null) {
                throw new InvalidInputException("Each attendance row must include a student.");
            }
            Student student = enrolled.get(entry.studentId());
            if (student == null) {
                throw new InvalidInputException(
                        "Attendance was submitted for a student who is not enrolled in this subject.");
            }
            AttendanceStatus status = parseStatus(entry.status());
            String remarks = entry.remarks() == null ? null : entry.remarks().trim();
            if (remarks != null && remarks.length() > 255) {
                throw new InvalidInputException("Remarks must be 255 characters or fewer.");
            }
            attendanceRepository.upsert(
                    student.getId(), course.getId(), day, status, remarks, facultyId);
        }

        auditLog.record("UPDATE", "ATTENDANCE",
                "Saved attendance for " + course.getCourseCode() + " on " + day + ".");
        return buildSheet(course, day);
    }

    public MyAttendance myAttendance() {
        Student student = access.requireCurrentStudent();
        List<Course> courses = courseRepository.findActiveByDepartmentAndSemester(
                student.getDepartment(), student.getSemester());
        List<AttendanceRecord> all = attendanceRepository.findByStudent(student.getId());
        Map<Long, List<AttendanceRecord>> byCourse = new LinkedHashMap<>();
        for (AttendanceRecord record : all) {
            byCourse.computeIfAbsent(record.getCourseId(), key -> new ArrayList<>()).add(record);
        }

        boolean lateCountsAsPresent = settingsService.get().isLateCountsAsPresent();
        List<CourseAttendance> courseViews = new ArrayList<>();
        for (Course course : courses) {
            List<AttendanceRecord> records = byCourse.getOrDefault(course.getId(), List.of());
            courseViews.add(new CourseAttendance(
                    course,
                    AttendanceSummary.from(records, lateCountsAsPresent),
                    records));
        }

        return new MyAttendance(
                StudentSummary.from(student),
                courseViews,
                AttendanceSummary.from(all, lateCountsAsPresent));
    }

    private AttendanceSheet buildSheet(Course course, LocalDate day) {
        Map<Long, AttendanceRecord> existing =
                attendanceRepository.findByCourseAndDate(course.getId(), day).stream()
                        .collect(Collectors.toMap(AttendanceRecord::getStudentId, Function.identity()));
        List<AttendanceRow> rows = new ArrayList<>();
        for (Student student : access.enrolledStudents(course)) {
            AttendanceRecord record = existing.get(student.getId());
            rows.add(new AttendanceRow(
                    StudentSummary.from(student),
                    record == null || record.getStatus() == null
                            ? null : record.getStatus().name(),
                    record == null ? null : record.getRemarks(),
                    record == null ? null : record.getId(),
                    record == null ? null : record.getMarkedByName(),
                    record == null ? null : record.getMarkedAt(),
                    record == null ? null : record.getUpdatedByName(),
                    record == null ? null : record.getUpdatedAt()));
        }
        return new AttendanceSheet(course, day, rows);
    }

    private LocalDate requireDate(LocalDate date) {
        if (date == null) {
            throw new InvalidInputException("Attendance date is required.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new InvalidInputException("Attendance cannot be marked for a future date.");
        }
        return date;
    }

    private AttendanceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new InvalidInputException("Each attendance row must have a status.");
        }
        try {
            return AttendanceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException("Attendance status must be Present, Absent or Late.");
        }
    }
}
