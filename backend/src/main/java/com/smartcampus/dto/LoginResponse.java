package com.smartcampus.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response of {@code POST /api/auth/login}.
 *
 * @param token   opaque bearer token stored in the browser and sent back on
 *                every protected request as {@code Authorization: Bearer ...}
 * @param user    safe profile information (never contains a password)
 * @param expiresAt when the session stops being valid
 */
public record LoginResponse(String token, UserInfo user, LocalDateTime expiresAt, List<String> permissions) {
}
