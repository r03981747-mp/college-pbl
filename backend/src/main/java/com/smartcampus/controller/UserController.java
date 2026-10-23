package com.smartcampus.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartcampus.dto.ApiResponse;
import com.smartcampus.dto.CreateUserRequest;
import com.smartcampus.dto.PagedResponse;
import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.dto.UserInfo;
import com.smartcampus.model.enums.AccountStatus;
import com.smartcampus.model.enums.Role;
import com.smartcampus.security.RequireRoles;
import com.smartcampus.service.UserService;

/**
 * Administrator-only API for login accounts.
 *
 * <pre>
 * GET    /api/users                 list + search + filter + sort + page
 * GET    /api/users/options         every account (drop-downs)
 * GET    /api/users/{id}            one record
 * POST   /api/users                 create   201
 * PUT    /api/users/{id}            update   200
 * PUT    /api/users/{id}/status     enable / disable   200
 * DELETE /api/users/{id}            delete   200
 * </pre>
 *
 * <p>Responses only ever contain {@link UserInfo} - there is no password field
 * in this DTO, so a BCrypt hash can never leak through the API.</p>
 */
@RestController
@RequestMapping("/api/users")
@RequireRoles(Role.ADMIN)
public class UserController {

    /** Body of the enable / disable action. */
    public record StatusRequest(AccountStatus status) {
    }

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<UserInfo>>> list(
            @RequestParam Map<String, String> params) {
        PagedResponse<UserInfo> page = userService.list(SearchCriteria.from(params));
        return ResponseEntity.ok(ApiResponse.ok(page,
                page.getTotalElements() + " account(s) found"));
    }

    /** Every account - used by "requested by" and "assign faculty" drop-downs. */
    @GetMapping("/options")
    public ResponseEntity<ApiResponse<List<UserInfo>>> options() {
        List<UserInfo> users = userService.all();
        return ResponseEntity.ok(ApiResponse.ok(users, users.size() + " account(s)"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserInfo>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getById(id), "Account found"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserInfo>> create(@RequestBody CreateUserRequest request) {
        UserInfo created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(created, "Account created successfully."));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserInfo>> update(
            @PathVariable Long id, @RequestBody CreateUserRequest request) {
        UserInfo updated = userService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Account updated successfully."));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserInfo>> changeStatus(
            @PathVariable Long id, @RequestBody StatusRequest body) {
        UserInfo updated = userService.changeStatus(id, body.status());
        return ResponseEntity.ok(ApiResponse.ok(updated,
                "Account is now " + updated.getStatus().getLabel()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Account deleted successfully."));
    }
}
