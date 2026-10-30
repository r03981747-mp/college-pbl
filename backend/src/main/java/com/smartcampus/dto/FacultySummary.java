package com.smartcampus.dto;

import com.smartcampus.model.Faculty;

/** Minimal faculty identity for class teacher / mentor cards. */
public record FacultySummary(
        Long id,
        String name,
        String employeeId,
        String designation,
        String department,
        String email) {

    public static FacultySummary from(Faculty faculty) {
        if (faculty == null) {
            return null;
        }
        return new FacultySummary(
                faculty.getId(),
                faculty.getName(),
                faculty.getEmployeeId(),
                faculty.getDesignation(),
                faculty.getDepartment(),
                faculty.getEmail());
    }
}
