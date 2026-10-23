package com.smartcampus.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartcampus.dto.ApiResponse;
import com.smartcampus.dto.ConcurrencyTestRequest;
import com.smartcampus.dto.ConcurrencyTestResult;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.ConcurrencyDemoService;
import com.smartcampus.service.ConcurrencyDemoService.ResourceOption;

/**
 * Administrator-only endpoint that <b>demonstrates multithreading and
 * synchronisation live</b> during the viva.
 *
 * <pre>
 * GET  /api/concurrency/resources   resources offered in the drop-down
 * POST /api/concurrency/run         start N threads competing for one slot
 * GET  /api/concurrency/count       total bookings after the test
 * </pre>
 *
 * <p>Calling {@code POST /api/concurrency/run} with 5 attempts produces
 * exactly <b>1 success and 4 rejections</b>, each rejection carrying the
 * conflict message.  The result object reports the elapsed time and one line
 * per thread, which is exactly what should be shown on screen.</p>
 */
@RestController
@RequestMapping("/api/concurrency")
@RequireRoles(Role.ADMIN)
public class ConcurrencyController {

    private final ConcurrencyDemoService concurrencyDemoService;

    public ConcurrencyController(ConcurrencyDemoService concurrencyDemoService) {
        this.concurrencyDemoService = concurrencyDemoService;
    }

    /** Every bookable resource, so the demonstration can target any of them. */
    @GetMapping("/resources")
    public ResponseEntity<ApiResponse<List<ResourceOption>>> resources() {
        List<ResourceOption> options = concurrencyDemoService.sampleResources();
        return ResponseEntity.ok(ApiResponse.ok(options, options.size() + " resource(s)"));
    }

    /** Fires the threads and reports what each of them experienced. */
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<ConcurrencyTestResult>> run(
            @RequestBody ConcurrencyTestRequest request) {
        ConcurrencyTestResult result = concurrencyDemoService.run(request);
        return ResponseEntity.ok(ApiResponse.ok(result, result.getSummary()));
    }

    /** Number of bookings in the table right now (shown after the test). */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> count() {
        long total = concurrencyDemoService.bookingCount();
        return ResponseEntity.ok(ApiResponse.ok(total, total + " booking(s) in total"));
    }
}
