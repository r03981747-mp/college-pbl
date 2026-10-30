package com.smartcampus.dto;

import com.smartcampus.model.Student;

/**
 * Academic-safe student identity.  Phone, address and date of birth are
 * deliberately omitted because faculty only need academic context.
 */
public record StudentSummary(
        Long id,
        String usn,
        String name,
        String email,
        String department,
        int semester,
        String section,
        int admissionYear,
        String status,
        Long classTeacherId,
        String classTeacherName,
        Long mentorId,
        String mentorName) {

    public static StudentSummary from(Student student) {
        return new StudentSummary(
                student.getId(),
                student.getUsn(),
                student.getName(),
                student.getEmail(),
                student.getDepartment(),
                student.getSemester(),
                student.getSection(),
                student.getAdmissionYear(),
                student.getStatus() == null ? null : student.getStatus().name(),
                student.getClassTeacherId(),
                student.getClassTeacherName(),
                student.getMentorId(),
                student.getMentorName());
    }
}
