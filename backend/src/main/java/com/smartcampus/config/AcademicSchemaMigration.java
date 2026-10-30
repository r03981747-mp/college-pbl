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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/**
 * Applies the additive academic schema on databases created before the marks,
 * attendance, assignment and eligibility modules existed.
 *
 * <p>The migration is idempotent: columns, indexes and foreign keys are checked
 * in {@code information_schema} before they are created, and every table uses
 * {@code CREATE TABLE IF NOT EXISTS}.  It deliberately avoids the
 * {@code PREPARE}/{@code EXECUTE} dynamic SQL used by the manual
 * {@code database/academic_migration.sql}, because TiDB does not support that
 * form.  Failures are logged instead of stopping the application; an operator
 * can still run the SQL file manually.</p>
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

            ensureColumn(jdbc, "students", "class_teacher_id",
                    "ALTER TABLE students ADD COLUMN class_teacher_id INT NULL "
                            + "COMMENT 'Faculty member assigned as class teacher'");
            ensureColumn(jdbc, "students", "mentor_id",
                    "ALTER TABLE students ADD COLUMN mentor_id INT NULL "
                            + "COMMENT 'Faculty member assigned as mentor'");
            ensureIndex(jdbc, "students", "idx_students_class_teacher",
                    "ALTER TABLE students ADD INDEX idx_students_class_teacher (class_teacher_id)");
            ensureIndex(jdbc, "students", "idx_students_mentor",
                    "ALTER TABLE students ADD INDEX idx_students_mentor (mentor_id)");
            ensureForeignKey(jdbc, "students", "fk_student_class_teacher",
                    "ALTER TABLE students ADD CONSTRAINT fk_student_class_teacher "
                            + "FOREIGN KEY (class_teacher_id) REFERENCES faculty (id) ON DELETE SET NULL");
            ensureForeignKey(jdbc, "students", "fk_student_mentor",
                    "ALTER TABLE students ADD CONSTRAINT fk_student_mentor "
                            + "FOREIGN KEY (mentor_id) REFERENCES faculty (id) ON DELETE SET NULL");

            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection,
                        new EncodedResource(new ClassPathResource("db/academic_tables.sql"),
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

    private void ensureColumn(JdbcTemplate jdbc, String table, String column, String ddl) {
        if (!columnExists(jdbc, table, column)) {
            try {
                jdbc.execute(ddl);
                log.info("Added column {}.{}", table, column);
            } catch (DataAccessException ex) {
                // A second application instance may have added it between the
                // check and the DDL.  The next startup will verify it again.
                log.debug("Column {}.{} already present or could not be added: {}",
                        table, column, ex.getMessage());
            }
        }
    }

    private void ensureIndex(JdbcTemplate jdbc, String table, String index, String ddl) {
        if (!indexExists(jdbc, table, index)) {
            try {
                jdbc.execute(ddl);
                log.info("Added index {} on {}", index, table);
            } catch (DataAccessException ex) {
                // Another instance may have created it between the check and the DDL.
                log.debug("Index {} already present or could not be added: {}", index, ex.getMessage());
            }
        }
    }

    private void ensureForeignKey(JdbcTemplate jdbc, String table, String constraint, String ddl) {
        if (!foreignKeyExists(jdbc, table, constraint)) {
            try {
                jdbc.execute(ddl);
                log.info("Added foreign key {} on {}", constraint, table);
            } catch (DataAccessException ex) {
                // Foreign keys are optional for the application queries.  Some
                // managed MySQL services restrict or ignore them.
                log.debug("Foreign key {} already present or could not be added: {}", constraint, ex.getMessage());
            }
        }
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

    private boolean indexExists(JdbcTemplate jdbc, String tableName, String indexName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class, tableName, indexName);
        return count != null && count > 0;
    }

    private boolean foreignKeyExists(JdbcTemplate jdbc, String tableName, String constraintName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                        + "AND CONSTRAINT_NAME = ? AND CONSTRAINT_TYPE = 'FOREIGN KEY'",
                Integer.class, tableName, constraintName);
        return count != null && count > 0;
    }
}
