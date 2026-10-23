package com.smartcampus.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartcampus.model.ActivityLog;
import com.smartcampus.repository.ActivityLogRepository;
import com.smartcampus.security.AuthContext;

/**
 * Records what happened in the system, in <b>two places at once</b>:
 *
 * <ol>
 *   <li>the {@code activity_logs} MySQL table (queryable), and</li>
 *   <li>a plain text file under {@code logs/} - the <b>file handling</b>
 *       requirement of the PBL, written with {@link BufferedWriter}.</li>
 * </ol>
 *
 * <p>The audit trail must never break the operation it is describing, so every
 * failure here is caught and logged instead of propagated.</p>
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ActivityLogRepository repository;

    @Value("${app.log.directory:logs}")
    private String logDirectory;

    public AuditLogService(ActivityLogRepository repository) {
        this.repository = repository;
    }

    /** Stores the event in MySQL and appends it to the activity log file. */
    public void record(String action, String module, String details) {
        record(AuthContext.userId(), AuthContext.displayName(), action, module, details);
    }

    /** Same, with an explicit acting user (used when nobody is signed in). */
    public void record(Long userId, String userName, String action,
                       String module, String details) {
        try {
            ActivityLog entry = new ActivityLog();
            entry.setUserId(userId);
            entry.setUserName(userName);
            entry.setAction(action);
            entry.setModule(module);
            entry.setDetails(details);
            repository.save(entry);
        } catch (Exception ex) {
            log.warn("Could not persist activity log row: {}", ex.getMessage());
        }
        appendToFile(action, module, details);
    }

    // ==================================================================
    //  FILE HANDLING - append one line per event to logs/activity.log
    // ==================================================================

    private void appendToFile(String action, String module, String details) {
        Path directory = Paths.get(logDirectory);
        Path file = directory.resolve("activity-" + LocalDate.now() + ".log");

        String line = LocalDateTime.now().format(TIMESTAMP)
                + " | " + pad(action, 8)
                + " | " + pad(module, 12)
                + " | " + AuthContext.username()
                + " | " + (details == null ? "" : details.replace('\n', ' '))
                + System.lineSeparator();

        try {
            Files.createDirectories(directory);           // creates logs/ when missing
            try (BufferedWriter writer = Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,          // create if absent
                    StandardOpenOption.APPEND)) {       // append, never overwrite
                writer.write(line);
            }
        } catch (IOException ex) {
            // a failure to write a log file must never fail the user's request
            log.warn("Could not append to activity log file {}: {}", file, ex.getMessage());
        }
    }

    private String pad(String value, int width) {
        if (value == null) {
            return "";
        }
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }

    /** Absolute path of today's log file - returned by the reports page. */
    public Path currentLogFile() {
        return Paths.get(logDirectory).resolve("activity-" + LocalDate.now() + ".log");
    }

    /** Newest audit entries, shown on the reports page. */
    public java.util.List<ActivityLog> recent(int limit) {
        return repository.findRecent(Math.max(1, Math.min(limit, 500)));
    }

    /** How many events have been recorded in total. */
    public long total() {
        return repository.count(new com.smartcampus.dto.SearchCriteria());
    }

    /** Audit trail grouped by module (small chart on the reports page). */
    public java.util.Map<String, Long> countByModule() {
        return repository.countByModule();
    }
}
