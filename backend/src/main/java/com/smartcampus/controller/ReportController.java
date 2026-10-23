package com.smartcampus.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartcampus.dto.ApiResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.model.ActivityLog;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.AuditLogService;
import com.smartcampus.service.ReportService;
import com.smartcampus.service.ReportService.GeneratedReport;

/**
 * Reporting API: on-screen summaries and downloadable CSV exports.
 *
 * <pre>
 * GET /api/reports/types          supported report names
 * GET /api/reports/audit          recent audit-trail entries (DB + log file)
 * GET /api/reports/{type}         CSV download, e.g. /api/reports/students
 * </pre>
 *
 * <p>The CSV is produced by {@link ReportService} with {@code BufferedWriter}
 * (file handling), written to {@code reports/} on disk and then streamed back
 * to the browser with {@code Content-Disposition: attachment}.</p>
 */
@RestController
@RequestMapping("/api/reports")
@RequireRoles({Role.ADMIN, Role.FACULTY})
public class ReportController {

    private final ReportService reportService;
    private final AuditLogService auditLogService;

    public ReportController(ReportService reportService, AuditLogService auditLogService) {
        this.reportService = reportService;
        this.auditLogService = auditLogService;
    }

    /** Report names shown as buttons on the reports page. */
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<String>>> types() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.supportedTypes(),
                "Supported report types"));
    }

    /** Newest audit events - proof that both the table and the log file work. */
    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> audit(
            @RequestParam(defaultValue = "50") int limit) {
        List<ActivityLog> entries = auditLogService.recent(limit);
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("entries", entries,
                       "byModule", auditLogService.countByModule(),
                       "logFile", auditLogService.currentLogFile().toString()),
                entries.size() + " audit entr(y/ies)"));
    }

    /**
     * Generates the report and streams it as a CSV download.
     *
     * <p>Supports the same keyword/filters as the management pages, so
     * "export what I am looking at" works out of the box.</p>
     */
    @GetMapping("/{type}")
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam Map<String, String> params) {
        GeneratedReport report = reportService.export(type, SearchCriteria.from(params));

        byte[] body = report.content().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report.fileName() + "\"")
                .header("X-Report-Rows", String.valueOf(report.rowCount()))
                .contentType(MediaType.parseMediaType(report.contentType()))
                .contentLength(body.length)
                .body(body);
    }
}
