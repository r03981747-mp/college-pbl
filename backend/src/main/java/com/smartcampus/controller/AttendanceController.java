package com.smartcampus.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartcampus.dto.ApiResponse;
import com.smartcampus.dto.AttendanceSaveRequest;
import com.smartcampus.dto.AttendanceSheet;
import com.smartcampus.dto.MyAttendance;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.AttendanceService;

/** Faculty attendance register and the student self-view. */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/sheet")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<AttendanceSheet>> sheet(
            @RequestParam Long courseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceService.sheet(courseId, date), "Attendance register"));
    }

    @PutMapping("/sheet")
    @RequireRoles({Role.ADMIN, Role.FACULTY})
    public ResponseEntity<ApiResponse<AttendanceSheet>> save(
            @RequestBody AttendanceSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceService.save(request), "Attendance saved."));
    }

    @GetMapping("/my")
    @RequireRoles(Role.STUDENT)
    public ResponseEntity<ApiResponse<MyAttendance>> myAttendance() {
        return ResponseEntity.ok(ApiResponse.ok(
                attendanceService.myAttendance(), "Your attendance"));
    }
}
