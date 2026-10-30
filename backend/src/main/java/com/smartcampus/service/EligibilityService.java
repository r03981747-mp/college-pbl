package com.smartcampus.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.AttendanceSummary;
import com.smartcampus.dto.EligibilityCheck;
import com.smartcampus.dto.EligibilityResult;
import com.smartcampus.model.AcademicSettings;
import com.smartcampus.model.AttendanceRecord;
import com.smartcampus.model.Student;
import com.smartcampus.model.StudentMark;
import com.smartcampus.repository.AttendanceRepository;
import com.smartcampus.repository.MarkRepository;

/**
 * Derives eligibility from current marks/attendance.  No eligibility status is
 * stored, so changing a threshold or correcting a mark immediately changes the
 * result.
 */
@Service
public class EligibilityService {

    private final AcademicSettingsService settingsService;
    private final MarkRepository markRepository;
    private final AttendanceRepository attendanceRepository;

    public EligibilityService(AcademicSettingsService settingsService,
                              MarkRepository markRepository,
                              AttendanceRepository attendanceRepository) {
        this.settingsService = settingsService;
        this.markRepository = markRepository;
        this.attendanceRepository = attendanceRepository;
    }

    /**
     * @param includeUnpublishedMarks true for faculty/admin views; students only
     *                                see published assessments
     */
    public EligibilityResult calculate(Student student, boolean includeUnpublishedMarks) {
        AcademicSettings settings = settingsService.get();

        List<AttendanceRecord> attendance =
                attendanceRepository.findByStudent(student.getId());
        AttendanceSummary attendanceSummary = AttendanceSummary.from(
                attendance, settings.isLateCountsAsPresent());

        List<StudentMark> marks = markRepository.findByStudent(
                student.getId(), !includeUnpublishedMarks);
        List<StudentMark> scored = marks.stream()
                .filter(mark -> mark.getScore() != null)
                .toList();

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalMax = BigDecimal.ZERO;
        for (StudentMark mark : scored) {
            totalScore = totalScore.add(mark.getScore());
            if (mark.getMaxScore() != null) {
                totalMax = totalMax.add(mark.getMaxScore());
            }
        }
        BigDecimal marksPercentage = totalMax.compareTo(BigDecimal.ZERO) == 0
                ? null
                : totalScore.multiply(new BigDecimal("100"))
                        .divide(totalMax, 2, RoundingMode.HALF_UP);

        List<EligibilityCheck> checks = new ArrayList<>();

        if (!settings.isRulesConfigured()) {
            checks.add(new EligibilityCheck(
                    "Eligibility rules",
                    "PENDING",
                    "An administrator has not configured the eligibility thresholds yet."));
        }

        if (!settings.isRequireAttendance()) {
            checks.add(new EligibilityCheck("Attendance", "NOT_REQUIRED",
                    "Attendance is not part of the configured eligibility rules."));
        } else if (attendanceSummary.total() < settings.getMinAttendanceRecords()) {
            checks.add(new EligibilityCheck("Attendance", "PENDING",
                    "Only " + attendanceSummary.total() + " attendance record(s); at least "
                            + settings.getMinAttendanceRecords() + " are required."));
        } else if (attendanceSummary.percentage() == null) {
            checks.add(new EligibilityCheck("Attendance", "PENDING",
                    "No attendance has been recorded yet."));
        } else if (attendanceSummary.percentage()
                .compareTo(settings.getMinAttendancePercentage()) < 0) {
            checks.add(new EligibilityCheck("Attendance", "UNMET",
                    formatPercent(attendanceSummary.percentage())
                            + " attendance; the minimum is "
                            + formatPercent(settings.getMinAttendancePercentage()) + "."));
        } else {
            checks.add(new EligibilityCheck("Attendance", "MET",
                    formatPercent(attendanceSummary.percentage())
                            + " attendance; the minimum is "
                            + formatPercent(settings.getMinAttendancePercentage()) + "."));
        }

        if (!settings.isRequireMarks()) {
            checks.add(new EligibilityCheck("Marks", "NOT_REQUIRED",
                    "Marks are not part of the configured eligibility rules."));
        } else if (scored.size() < settings.getMinAssessmentsRequired()) {
            checks.add(new EligibilityCheck("Marks", "PENDING",
                    "Only " + scored.size() + " assessed submission(s); at least "
                            + settings.getMinAssessmentsRequired() + " are required."));
        } else if (marksPercentage == null) {
            checks.add(new EligibilityCheck("Marks", "PENDING",
                    "No marks have been entered yet."));
        } else if (marksPercentage.compareTo(settings.getMinMarksPercentage()) < 0) {
            checks.add(new EligibilityCheck("Marks", "UNMET",
                    formatPercent(marksPercentage) + " marks; the minimum is "
                            + formatPercent(settings.getMinMarksPercentage()) + "."));
        } else {
            checks.add(new EligibilityCheck("Marks", "MET",
                    formatPercent(marksPercentage) + " marks; the minimum is "
                            + formatPercent(settings.getMinMarksPercentage()) + "."));
        }

        String status;
        if (!settings.isRulesConfigured()) {
            status = "PENDING";
        } else if (checks.stream().anyMatch(check -> "UNMET".equals(check.result()))) {
            status = "NOT_ELIGIBLE";
        } else if (checks.stream().anyMatch(check -> "PENDING".equals(check.result()))) {
            status = "PENDING";
        } else {
            status = "ELIGIBLE";
        }

        String summary = switch (status) {
            case "ELIGIBLE" -> "Eligible under the configured academic rules.";
            case "NOT_ELIGIBLE" -> "Not eligible under the configured academic rules.";
            default -> "Pending - more data or administrator configuration is required.";
        };

        return new EligibilityResult(
                status,
                summary,
                attendanceSummary.percentage(),
                marksPercentage,
                attendanceSummary.total(),
                scored.size(),
                settings.getMinAttendancePercentage(),
                settings.getMinMarksPercentage(),
                settings.getMinAssessmentsRequired(),
                settings.getMinAttendanceRecords(),
                settings.isRulesConfigured(),
                checks);
    }

    private String formatPercent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
