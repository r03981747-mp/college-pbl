package com.smartcampus.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smartcampus.dto.CreateUserRequest;
import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.dto.UserInfo;
import com.smartcampus.exception.DuplicateRecordException;
import com.smartcampus.exception.ForbiddenException;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.exception.UserNotFoundException;
import com.smartcampus.model.UserAccount;
import com.smartcampus.model.enums.AccountStatus;
import com.smartcampus.model.enums.Role;
import com.smartcampus.repository.SessionRepository;
import com.smartcampus.repository.UserRepository;
import com.smartcampus.security.AuthContext;
import com.smartcampus.security.PasswordService;
import com.smartcampus.utility.Validators;

/**
 * Administrator-facing management of login accounts.
 *
 * <p>Belongs to the management modules, not to authentication: it creates,
 * edits, disables and deletes accounts.  The only security-sensitive part is
 * delegating password hashing to {@link PasswordService}, so a plain-text
 * password is never stored.</p>
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordService passwordService;
    private final AuditLogService auditLog;

    public UserService(UserRepository userRepository,
                       SessionRepository sessionRepository,
                       PasswordService passwordService,
                       AuditLogService auditLog) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordService = passwordService;
        this.auditLog = auditLog;
    }

    // ==================================================================
    //  Read
    // ==================================================================

    public PagedResponse<UserInfo> list(SearchCriteria criteria) {
        criteria.normalise();
        List<UserInfo> content = userRepository.search(criteria).stream()
                .map(this::toInfo)
                .toList();
        long total = userRepository.count(criteria);
        return PagedResponse.of(content, criteria, total);
    }

    public UserInfo getById(Long id) {
        return toInfo(userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id)));
    }

    /** Every account - used by the "requested by" drop-downs. */
    public List<UserInfo> all() {
        return userRepository.findAll().stream().map(this::toInfo).toList();
    }

    // ==================================================================
    //  Write
    // ==================================================================

    public UserInfo create(CreateUserRequest request) {
        validate(request, null);
        ensureUnique(request, null);

        UserAccount user = new UserAccount();
        apply(request, user);
        // BCrypt hashing happens here - the raw password is never persisted
        user.setPassword(passwordService.encode(request.getPassword()));

        UserAccount saved = userRepository.save(user);
        auditLog.record("CREATE", "USER",
                "Created account " + saved.getUsername() + " (" + saved.getRole() + ")");
        return toInfo(saved);
    }

    public UserInfo update(Long id, CreateUserRequest request) {
        UserAccount existing = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        validate(request, id);
        ensureUnique(request, id);
        apply(request, existing);

        // a blank password keeps the previous hash
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            existing.setPassword(passwordService.encode(request.getPassword()));
        }

        UserAccount saved = userRepository.update(existing);

        if (saved.getStatus() == AccountStatus.INACTIVE) {
            // a disabled account must not keep its open sessions
            sessionRepository.deleteByUserId(saved.getId());
        }

        auditLog.record("UPDATE", "USER",
                "Updated account " + saved.getUsername() + " (" + saved.getRole() + ")");
        return toInfo(saved);
    }

    public UserInfo changeStatus(Long id, AccountStatus status) {
        UserAccount user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (status == null) {
            throw new InvalidInputException("Status", "is required");
        }

        user.setStatus(status);
        userRepository.update(user);

        if (status == AccountStatus.INACTIVE) {
            sessionRepository.deleteByUserId(user.getId());
        }

        auditLog.record("UPDATE", "USER",
                user.getUsername() + " status -> " + status.getLabel());
        return toInfo(user);
    }

    public void delete(Long id) {
        UserAccount user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        // never let an administrator lock himself out of the system
        Long currentId = AuthContext.userId();
        if (currentId != null && currentId.equals(id)) {
            throw new ForbiddenException("You cannot delete your own account.");
        }
        if (user.getRole() == Role.ADMIN && countAdmins() <= 1) {
            throw new ForbiddenException(
                    "This is the last administrator account and cannot be deleted.");
        }

        userRepository.deleteById(id);
        auditLog.record("DELETE", "USER", "Deleted account " + user.getUsername());
    }

    public long countAdmins() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .count();
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private void validate(CreateUserRequest request, Long id) {
        String username = Validators.requiredText(request.getUsername(), "Username", 80)
                .toLowerCase();
        if (!username.matches("^[a-z0-9._-]{3,80}$")) {
            throw new InvalidInputException("Username",
                    "may only contain letters, digits, dot, underscore and hyphen");
        }
        request.setUsername(username);

        request.setEmail(Validators.email(request.getEmail(), "Email"));
        request.setFullName(Validators.requiredText(request.getFullName(), "Full name", 120));

        if (request.getRole() == null) {
            throw new InvalidInputException("Role", "is required");
        }
        if (request.getStatus() == null) {
            request.setStatus(AccountStatus.ACTIVE);
        }

        boolean isCreate = id == null;
        if (isCreate && (request.getPassword() == null || request.getPassword().isBlank())) {
            throw new InvalidInputException("Password", "is required");
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()
                && request.getPassword().length() < 6) {
            throw new InvalidInputException("Password", "must be at least 6 characters");
        }
    }

    private void ensureUnique(CreateUserRequest request, Long id) {
        if (userRepository.existsByUsername(request.getUsername(), id)) {
            throw new DuplicateRecordException("Username", request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail(), id)) {
            throw new DuplicateRecordException("Email", request.getEmail());
        }
    }

    private void apply(CreateUserRequest request, UserAccount user) {
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
    }

    /** Converts the entity to the safe DTO (no password, no hash). */
    private UserInfo toInfo(UserAccount user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getEmail(),
                user.getFullName(), user.getRole(), user.getStatus());
    }
}
