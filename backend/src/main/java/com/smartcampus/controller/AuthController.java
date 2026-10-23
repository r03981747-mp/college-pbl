package com.smartcampus.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartcampus.dto.ApiResponse;
import com.smartcampus.dto.ChangePasswordRequest;
import com.smartcampus.dto.LoginRequest;
import com.smartcampus.dto.LoginResponse;
import com.smartcampus.security.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Authentication endpoints.
 *
 * <p>This controller is deliberately small: it only answers "who is this
 * user?".  Business modules (students, courses, bookings...) live in their own
 * controllers and simply consume the identity that the security filter has
 * already established.</p>
 *
 * <pre>
 * POST /api/auth/login            -&gt; verify credentials, open a session
 * POST /api/auth/logout           -&gt; destroy the current session
 * GET  /api/auth/me               -&gt; current user + permissions   (401 when signed out)
 * GET  /api/auth/status           -&gt; public "am I signed in?" check (never 401)
 * POST /api/auth/change-password  -&gt; self-service password change
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Step 1-7 of the authentication flow: validate, verify, issue token. */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Signed in successfully"));
    }

    /** Destroys the token carried by this request. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
        authService.logout(extractToken(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(null, "Signed out successfully"));
    }

    /** The signed-in user as seen by the server (role comes from the database). */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me() {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(), "Current user"));
    }

    /**
     * Public session probe used by the front end on page load.
     * Returns {@code authenticated:false} instead of 401 when nobody is signed in.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(HttpServletRequest httpRequest) {
        Map<String, Object> status = authService.status(extractToken(httpRequest));
        boolean authenticated = Boolean.TRUE.equals(status.get("authenticated"));
        return ResponseEntity.ok(ApiResponse.ok(status,
                authenticated ? "Session is active" : "No active session"));
    }

    /** Re-hashes the new password and closes every other session. */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.ok(null,
                "Password changed. Please sign in again."));
    }

    // ------------------------------------------------------------------

    /** Reads {@code Authorization: Bearer <token>} or {@code X-Auth-Token}. */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return request.getHeader("X-Auth-Token");
    }
}
