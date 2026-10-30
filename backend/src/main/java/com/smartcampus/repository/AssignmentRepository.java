package com.smartcampus.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.model.Assignment;

/** JDBC queries for assignments. */
@Repository
public class AssignmentRepository extends JdbcSupport {

    private static final String BASE_SELECT = """
            SELECT a.id, a.course_id, c.course_code, c.course_name, c.department, c.semester,
                   a.title, a.instructions, a.due_at, a.allowed_file_types,
                   a.max_file_size_mb, a.max_score, a.allow_late, a.allow_resubmission,
                   a.published, a.created_by_faculty_id, f.name AS created_by_name,
                   a.created_at, a.updated_at
            FROM assignments a
            JOIN courses c ON c.id = a.course_id
            LEFT JOIN faculty f ON f.id = a.created_by_faculty_id """;

    private static final RowMapper<Assignment> ASSIGNMENT_ROW = (rs, rowNum) -> {
        Assignment assignment = new Assignment();
        assignment.setId(rs.getLong("id"));
        assignment.setCourseId(rs.getLong("course_id"));
        assignment.setCourseCode(str(rs, "course_code"));
        assignment.setCourseName(str(rs, "course_name"));
        assignment.setDepartment(str(rs, "department"));
        assignment.setSemester(intOf(rs, "semester"));
        assignment.setTitle(str(rs, "title"));
        assignment.setInstructions(str(rs, "instructions"));
        assignment.setDueAt(dateTime(rs, "due_at"));
        assignment.setAllowedFileTypes(str(rs, "allowed_file_types"));
        assignment.setMaxFileSizeMb(intOf(rs, "max_file_size_mb"));
        assignment.setMaxScore(decimal(rs, "max_score"));
        assignment.setAllowLate(rs.getBoolean("allow_late"));
        assignment.setAllowResubmission(rs.getBoolean("allow_resubmission"));
        assignment.setPublished(rs.getBoolean("published"));
        assignment.setCreatedByFacultyId(nullableLong(rs, "created_by_faculty_id"));
        assignment.setCreatedByName(str(rs, "created_by_name"));
        assignment.setCreatedAt(dateTime(rs, "created_at"));
        assignment.setUpdatedAt(dateTime(rs, "updated_at"));
        return assignment;
    };

    public AssignmentRepository(DataSource dataSource) {
        super(dataSource);
    }

    public Assignment save(Assignment assignment) {
        String sql = """
                INSERT INTO assignments
                    (course_id, title, instructions, due_at, allowed_file_types,
                     max_file_size_mb, max_score, allow_late, allow_resubmission,
                     published, created_by_faculty_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)""";
        long id = insert(sql,
                assignment.getCourseId(), assignment.getTitle(), assignment.getInstructions(),
                assignment.getDueAt(), assignment.getAllowedFileTypes(),
                assignment.getMaxFileSizeMb(), assignment.getMaxScore(),
                assignment.isAllowLate(), assignment.isAllowResubmission(),
                assignment.isPublished(), assignment.getCreatedByFacultyId());
        assignment.setId(id);
        return assignment;
    }

    public Assignment update(Assignment assignment) {
        String sql = """
                UPDATE assignments SET
                    title = ?, instructions = ?, due_at = ?, allowed_file_types = ?,
                    max_file_size_mb = ?, max_score = ?, allow_late = ?,
                    allow_resubmission = ?, published = ?
                WHERE id = ?""";
        int affected = update(sql,
                assignment.getTitle(), assignment.getInstructions(), assignment.getDueAt(),
                assignment.getAllowedFileTypes(), assignment.getMaxFileSizeMb(),
                assignment.getMaxScore(), assignment.isAllowLate(),
                assignment.isAllowResubmission(), assignment.isPublished(),
                assignment.getId());
        if (affected == 0) {
            throw new com.smartcampus.exception.SmartCampusException("Assignment not found.");
        }
        return assignment;
    }

    public Optional<Assignment> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE a.id = ?", ASSIGNMENT_ROW, id);
    }

    public List<Assignment> findByCourse(Long courseId, boolean publishedOnly) {
        String sql = BASE_SELECT + " WHERE a.course_id = ?"
                + (publishedOnly ? " AND a.published = TRUE" : "")
                + " ORDER BY a.due_at DESC, a.id DESC";
        return query(sql, ASSIGNMENT_ROW, courseId);
    }

    public List<Assignment> findByCourseIds(List<Long> courseIds, boolean publishedOnly) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> params = new ArrayList<>(courseIds);
        String sql = BASE_SELECT + " WHERE a.course_id IN (" + placeholders(courseIds.size()) + ")"
                + (publishedOnly ? " AND a.published = TRUE" : "")
                + " ORDER BY a.due_at DESC, a.id DESC";
        return query(sql, ASSIGNMENT_ROW, params.toArray());
    }

    public List<Assignment> findAll() {
        return query(BASE_SELECT + " ORDER BY a.due_at DESC, a.id DESC", ASSIGNMENT_ROW);
    }

    public boolean existsByCourseTitle(Long courseId, String title, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM assignments WHERE course_id = ? AND title = ?"
                + (excludeId == null ? "" : " AND id <> ?");
        if (excludeId == null) {
            return exists(sql, courseId, title);
        }
        return exists(sql, courseId, title, excludeId);
    }

    public boolean setPublished(Long id, boolean published) {
        return update("UPDATE assignments SET published = ? WHERE id = ?", published, id) > 0;
    }

    public boolean deleteById(Long id) {
        return update("DELETE FROM assignments WHERE id = ?", id) > 0;
    }

    public long countByCourse(Long courseId) {
        return queryForLong("SELECT COUNT(*) FROM assignments WHERE course_id = ?", courseId);
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
