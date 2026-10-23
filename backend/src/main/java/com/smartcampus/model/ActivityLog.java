package com.smartcampus.model;

import java.time.LocalDateTime;

/**
 * One row of the audit trail written by {@code AuditLogService}.
 * The same data is also appended to a text file (file handling).
 */
public class ActivityLog extends BaseEntity {

    private Long userId;
    private String userName;
    private String action;
    private String module;
    private String details;

    public ActivityLog() {
        super();
    }

    public ActivityLog(Long id, Long userId, String userName, String action,
                       String module, String details, LocalDateTime createdAt) {
        super(id, createdAt);
        this.userId = userId;
        this.userName = userName;
        this.action = action;
        this.module = module;
        this.details = details;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
