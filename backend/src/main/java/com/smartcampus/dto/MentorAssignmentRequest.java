package com.smartcampus.dto;

/** Administrator request to assign or clear a student's class teacher/mentor. */
public record MentorAssignmentRequest(Long classTeacherId, Long mentorId) {
}
