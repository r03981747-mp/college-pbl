package com.smartcampus.repository;

import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.model.AcademicSettings;

/** Reads/writes the single {@code academic_settings} row (id = 1). */
@Repository
public class AcademicSettingsRepository extends JdbcSupport {

    private static final String BASE_SELECT = """
            SELECT s.id, s.min_attendance_percentage, s.min_marks_percentage,
                   s.min_assessments_required, s.min_attendance_records,
                   s.require_marks, s.require_attendance, s.late_counts_as_present,
                   s.rules_configured, s.updated_by_user_id,
                   u.full_name AS updated_by_name, s.updated_at
            FROM academic_settings s
            LEFT JOIN users u ON u.id = s.updated_by_user_id
            WHERE s.id = 1""";

    private static final RowMapper<AcademicSettings> SETTINGS_ROW = (rs, rowNum) -> {
        AcademicSettings settings = new AcademicSettings();
        settings.setId(rs.getLong("id"));
        settings.setMinAttendancePercentage(decimal(rs, "min_attendance_percentage"));
        settings.setMinMarksPercentage(decimal(rs, "min_marks_percentage"));
        settings.setMinAssessmentsRequired(intOf(rs, "min_assessments_required"));
        settings.setMinAttendanceRecords(intOf(rs, "min_attendance_records"));
        settings.setRequireMarks(rs.getBoolean("require_marks"));
        settings.setRequireAttendance(rs.getBoolean("require_attendance"));
        settings.setLateCountsAsPresent(rs.getBoolean("late_counts_as_present"));
        settings.setRulesConfigured(rs.getBoolean("rules_configured"));
        settings.setUpdatedByUserId(nullableLong(rs, "updated_by_user_id"));
        settings.setUpdatedByName(str(rs, "updated_by_name"));
        settings.setUpdatedAt(dateTime(rs, "updated_at"));
        return settings;
    };

    public AcademicSettingsRepository(DataSource dataSource) {
        super(dataSource);
    }

    public Optional<AcademicSettings> get() {
        return queryOne(BASE_SELECT, SETTINGS_ROW);
    }

    public AcademicSettings save(AcademicSettings settings, Long updatedByUserId) {
        String sql = """
                UPDATE academic_settings SET
                    min_attendance_percentage = ?,
                    min_marks_percentage = ?,
                    min_assessments_required = ?,
                    min_attendance_records = ?,
                    require_marks = ?,
                    require_attendance = ?,
                    late_counts_as_present = ?,
                    rules_configured = ?,
                    updated_by_user_id = ?
                WHERE id = 1""";
        int affected = update(sql,
                settings.getMinAttendancePercentage(),
                settings.getMinMarksPercentage(),
                settings.getMinAssessmentsRequired(),
                settings.getMinAttendanceRecords(),
                settings.isRequireMarks(),
                settings.isRequireAttendance(),
                settings.isLateCountsAsPresent(),
                settings.isRulesConfigured(),
                updatedByUserId);

        if (affected == 0) {
            String insertSql = """
                    INSERT INTO academic_settings
                        (id, min_attendance_percentage, min_marks_percentage,
                         min_assessments_required, min_attendance_records,
                         require_marks, require_attendance, late_counts_as_present,
                         rules_configured, updated_by_user_id)
                    VALUES (1,?,?,?,?,?,?,?,?,?)""";
            insert(insertSql,
                    settings.getMinAttendancePercentage(),
                    settings.getMinMarksPercentage(),
                    settings.getMinAssessmentsRequired(),
                    settings.getMinAttendanceRecords(),
                    settings.isRequireMarks(),
                    settings.isRequireAttendance(),
                    settings.isLateCountsAsPresent(),
                    settings.isRulesConfigured(),
                    updatedByUserId);
        }
        return get().orElse(settings);
    }
}
