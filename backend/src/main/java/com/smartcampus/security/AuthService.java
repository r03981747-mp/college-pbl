package com.smartcampus.security;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartcampus.dto.ChangePasswordRequest;
import com.smartcampus.dto.LoginRequest;
import com.smartcampus.dto.LoginResponse;
import com.smartcampus.dto.UserInfo;
import com.smartcampus.exception.UnauthorizedException;
import com.smartcampus.exception.UserNotFoundException;
import com.smartcampus.model.SessionToken;
import com.smartcampus.model.UserAccount;
import com.smartcampus.repository.SessionRepository;
import com.smartcampus.repository.UserRepository;
import com.smartcampus.service.AuditLogService;

/**
 * The authentication module.  It owns <b>only</b> identity concerns:
 *
 * <ul>
 *   <li>login and credential verification</li>
 *   <li>password hashing / verification</li>
 *   <li>session (token) creation and destruction</li>
 *   <li>"who am I" and "am I signed in"</li>
 *   <li>role identification (read from the database)</li>
 *   <li>changing one's own password</li>
 * </ul>
 *
 * <p>Student/faculty/course/booking business logic deliberately lives
 * elsewhere - see the "Authentication boundaries" section of the
 * documentation.</p>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordService passwordService;
    private final AuditLogService auditLog;

    @Value("${app.session.ttl-minutes:720}")
    private long sessionTtlMinutes;

    public AuthService(UserRepository userRepository,
                       SessionRepository sessionRepository,
                       PasswordService passwordService,
                       AuditLogService auditLog) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordService = passwordService;
        this.auditLog = auditLog;
    }

    // ==================================================================
    //  Login
    // ==================================================================

    /**
     * Verifies the credentials and opens a session.
     *
     * <p>The failure message is deliberately identical for "unknown user",
     * "wrong password" and "disabled account" so the API cannot be used to
     * enumerate accounts.</p>
     */
    public LoginResponse login(LoginRequest request) {
        String credential = request.getUsername() == null ? "" : request.getUsername().trim();

        UserAccount user = userRepository.findByUsernameOrEmail(credential)
                .orElseThrow(AuthService::invalidCredentials);

        if (!user.canLogin()) {
            throw new UnauthorizedException("This account has been deactivated. "
                    + "Please contact the administrator.");
        }

        if (!passwordService.matches(request.getPassword(), user.getPassword())) {
            auditLog.record(user.getId(), user.getFullName(), "LOGIN_FAILED", "AUTH",
                    "Failed sign-in attempt for " + user.getUsername());
            throw invalidCredentials();
        }

        // session lifetime: "remember me" keeps the token for 7 days
        long ttlMinutes = request.isRememberMe()
                ? Math.max(sessionTtlMinutes, 60 * 24 * 7)
                : sessionTtlMinutes;

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(ttlMinutes);
        String token = newToken();
        sessionRepository.save(user.getId(), token, expiresAt);

        auditLog.record(user.getId(), user.getFullName(), "LOGIN", "AUTH",
                user.getUsername() + " signed in");

        return new LoginResponse(token, toUserInfo(user), expiresAt,
                List.copyOf(Permissions.forRole(user.getRole())));
    }

    // ==================================================================
    //  Logout
    // ==================================================================

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;                                     // nothing to destroy
        }
        sessionRepository.findByToken(token).ifPresent(session -> {
            sessionRepository.deleteByToken(token);
            userRepository.findById(session.getUserId()).ifPresent(user ->
                    auditLog.record(user.getId(), user.getFullName(), "LOGOUT", "AUTH",
                            user.getUsername() + " signed out"));
        });
    }

    // ==================================================================
    //  Status / identity
    // ==================================================================

    /**
     * Public endpoint used by the front end to decide whether to show the
     * login page or restore an existing session.  Never throws 401.
     */
    public Map<String, Object> status(String token) {
        Optional<UserAccount> user = token == null || token.isBlank()
                ? Optional.empty()
                : sessionRepository.findByToken(token)
                        .filter(SessionToken::isValid)
                        .map(SessionToken::getUserId)
                        .flatMap(userRepository::findById)
                        .filter(UserAccount::canLogin);

        return user
                .map(found -> Map.<String, Object>of(
                        "authenticated", true,
                        "user", toUserInfo(found),
                        "permissions", List.copyOf(Permissions.forRole(found.getRole()))))
                .orElse(Map.of("authenticated", false));
    }

    /** The signed-in user of the current request (used by {@code GET /api/auth/me}). */
    public Map<String, Object> me() {
        AuthenticatedUser current = AuthContext.get();
        if (current == null) {
            throw new UnauthorizedException("You are not signed in.");
        }
        UserAccount user = userRepository.findById(current.id())
                .filter(UserAccount::canLogin)
                .orElseThrow(() -> new UnauthorizedException("Your account is no longer active."));

        return Map.of(
                "user", toUserInfo(user),
                "permissions", List.copyOf(Permissions.forRole(user.getRole())));
    }

    // ==================================================================
    //  Password change (self service)
    // ==================================================================

    public void changePassword(ChangePasswordRequest request) {
        AuthenticatedUser current = AuthContext.get();
        if (current == null) {
            throw new UnauthorizedException("You are not signed in.");
        }

        UserAccount user = userRepository.findById(current.id())
                .orElseThrow(() -> new UserNotFoundException(current.id()));

        if (!passwordService.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new UnauthorizedException("Your current password is incorrect.");
        }

        // BCrypt again - the new value is hashed before it touches the database
        userRepository.updatePassword(user.getId(), passwordService.encode(request.getNewPassword()));

        // every other device holding this account is signed out
        sessionRepository.deleteByUserId(user.getId());

        auditLog.record(user.getId(), user.getFullName(), "PASSWORD_CHANGED", "AUTH",
                "Password changed for " + user.getUsername());
    }

    // ------------------------------------------------------------------

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("Invalid username or password");
    }

    /** Random 32 character session token (UUID without dashes). */
    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Never exposes the password hash. */
    private static UserInfo toUserInfo(UserAccount user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getEmail(),
                user.getFullName(), user.getRole(), user.getStatus());
    }
}
