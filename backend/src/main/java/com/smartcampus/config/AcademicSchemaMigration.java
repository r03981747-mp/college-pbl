package com.smartcampus.config;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/**
 * Applies the additive academic schema on databases created before the marks,
 * attendance, assignment and eligibility modules existed.
 *
 * <p>The migration is the same idempotent script used by
 * {@code database/academic_migration.sql}; it only adds columns, indexes and
 * tables.  It is skipped when the academic tables/columns are already present,
 * so normal restarts do not run DDL.  Failures are logged instead of stopping
 * the application: an operator can still run the SQL file manually.</p>
 */
@Component
public class AcademicSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AcademicSchemaMigration.class);

    private final DataSource dataSource;
    private final boolean enabled;

    public AcademicSchemaMigration(DataSource dataSource,
            @Value("${app.db.academic-migration-enabled:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            if (!tableExists(jdbc, "students")) {
                log.warn("Academic schema migration skipped: the base students table is missing. "
                        + "Load database/deploy_schema.sql first.");
                return;
            }
            if (academicSchemaReady(jdbc)) {
                return;
            }

            log.info("Academic schema is incomplete; applying the idempotent academic migration.");
            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection,
                        new EncodedResource(new ClassPathResource("db/academic_migration.sql"),
                                StandardCharsets.UTF_8));
            }
            log.info("Academic schema migration completed.");
        } catch (Exception ex) {
            log.warn("Academic schema migration did not complete: {}. "
                    + "Apply database/academic_migration.sql manually and redeploy.", ex.getMessage());
            log.debug("Academic schema migration failure", ex);
        }
    }

    private boolean academicSchemaReady(JdbcTemplate jdbc) {
        return tableExists(jdbc, "academic_settings")
                && tableExists(jdbc, "assessments")
                && tableExists(jdbc, "student_marks")
                && tableExists(jdbc, "attendance")
                && tableExists(jdbc, "assignments")
                && tableExists(jdbc, "assignment_submissions")
                && columnExists(jdbc, "students", "class_teacher_id")
                && columnExists(jdbc, "students", "mentor_id");
    }

    private boolean tableExists(JdbcTemplate jdbc, String tableName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(JdbcTemplate jdbc, String tableName, String columnName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
