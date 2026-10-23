package com.smartcampus.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartcampus.config.WebConfig.FrontendLocator;

/**
 * Entry point of the application.
 *
 * <p>Opening {@code http://localhost:8080/} redirects to the login page of the
 * bundled front end, so a single URL is enough to run the whole demo.  The
 * management pages themselves are static files served from {@code frontend/}.</p>
 *
 * <p>The browser-side guard in {@code auth.js} then forwards an already signed
 * in user to {@code index.html} (dashboard).</p>
 */
@RestController
public class HomeController {

    private final FrontendLocator frontendLocator;

    public HomeController(FrontendLocator frontendLocator) {
        this.frontendLocator = frontendLocator;
    }

    /** {@code /} -&gt; {@code /login.html} (302). */
    @GetMapping("/")
    public ResponseEntity<Void> home() {
        if (frontendLocator.isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", "/login.html")
                    .build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /** Human readable health check - handy when the API is tested with curl. */
    @GetMapping("/api/health")
    public ResponseEntity<java.util.Map<String, Object>> health() {
        return ResponseEntity.ok(java.util.Map.of(
                "status", "UP",
                "application", "Smart Campus Integrated Management System",
                "frontend", frontendLocator.isPresent()
                        ? String.valueOf(frontendLocator.directory())
                        : "not found next to the backend folder"));
    }
}
