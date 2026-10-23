package com.smartcampus.security;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.smartcampus.exception.ForbiddenException;
import com.smartcampus.exception.UnauthorizedException;
import com.smartcampus.model.SessionToken;
import com.smartcampus.model.UserAccount;
import com.smartcampus.repository.SessionRepository;
import com.smartcampus.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The <b>authentication filter</b> that every {@code /api/**} request passes
 * through before reaching a controller.
 *
 * <pre>
 *   request
 *      |-- skip? (/api/auth/login, /api/auth/status, CORS preflight)
 *      |-- extract "Authorization: Bearer &lt;token&gt;"
 *      |-- look the token up in user_sessions  (database)
 *      |-- load the account                   (database  ->  role comes from HERE)
 *      |-- AuthContext.set(user)              (ThreadLocal for this thread)
 *      |-- @RequireRoles check                (403 when the role is not allowed)
 *      v
 *   controller
 * </pre>
 *
 * <p>Failures throw the custom exceptions that
 * {@code GlobalExceptionHandler} converts into 401 / 403 JSON responses.</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** Endpoints reachable without a token. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/status",
            "/api/health");

    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    public AuthInterceptor(SessionRepository sessionRepository, UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        // CORS preflight never carries credentials
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (isPublic(path)) {
            return true;
        }

        // 1. credentials
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Authentication required. Please sign in.");
        }

        // 2. identity - resolved from the database, never from the request body
        AuthenticatedUser user = resolve(token);
        AuthContext.set(user);

        // 3. authorization
        if (handler instanceof HandlerMethod handlerMethod) {
            RequireRoles required = handlerMethod.getMethodAnnotation(RequireRoles.class);
            if (required == null) {
                required = handlerMethod.getBeanType().getAnnotation(RequireRoles.class);
            }
            if (required != null && !user.hasRole(required.value())) {
                throw ForbiddenException.requires(required.value());
            }
        }
        return true;
    }

    /**
     * Always runs when the request finishes - removes the ThreadLocal so a
     * pooled thread cannot leak one user's identity into the next request.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    // ------------------------------------------------------------------

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path);
    }

    /** Accepts {@code Authorization: Bearer x} or {@code X-Auth-Token: x}. */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return request.getHeader("X-Auth-Token");
    }

    private AuthenticatedUser resolve(String token) {
        Optional<AuthenticatedUser> resolved = sessionRepository.findByToken(token)
                .filter(SessionToken::isValid)
                .map(SessionToken::getUserId)
                .flatMap(userRepository::findById)
                .filter(UserAccount::canLogin)
                .map(user -> new AuthenticatedUser(
                        user.getId(), user.getUsername(), user.getFullName(),
                        user.getEmail(), user.getRole()));

        return resolved.orElseThrow(() -> new UnauthorizedException(
                "Your session has expired or is invalid. Please sign in again."));
    }
}
