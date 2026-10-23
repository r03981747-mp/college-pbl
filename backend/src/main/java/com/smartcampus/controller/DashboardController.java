package com.smartcampus.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartcampus.dto.ApiResponse;
import com.smartcampus.dto.DashboardStatistics;
import com.smartcampus.service.DashboardService;

/**
 * REST API behind the dashboard.
 *
 * <p>Every card and chart on the page is fed by this single call, and every
 * number inside it is a live aggregate query against MySQL - nothing is
 * hardcoded.</p>
 *
 * <pre>
 * GET /api/dashboard/statistics
 * </pre>
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Cards + charts + upcoming events + recent bookings in one response. */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<DashboardStatistics>> statistics() {
        DashboardStatistics statistics = dashboardService.statistics();
        return ResponseEntity.ok(ApiResponse.ok(statistics, "Dashboard statistics"));
    }
}
