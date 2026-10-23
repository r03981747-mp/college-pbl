package com.smartcampus.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.smartcampus.model.enums.Role;

/**
 * Authorization failure - the caller is authenticated but the role is not
 * allowed to perform the operation.  Maps to HTTP <b>403 Forbidden</b>.
 */
public class ForbiddenException extends SmartCampusException {

    private static final long serialVersionUID = 1L;

    public ForbiddenException(String message) {
        super(message);
    }

    /** Builds "Access denied: this action requires one of [ADMIN, FACULTY]". */
    public static ForbiddenException requires(Role... allowed) {
        String roles = Arrays.stream(allowed)
                .map(Enum::name)
                .collect(Collectors.joining(", ", "[", "]"));
        return new ForbiddenException("Access denied. This action requires one of these roles: " + roles);
    }
}
