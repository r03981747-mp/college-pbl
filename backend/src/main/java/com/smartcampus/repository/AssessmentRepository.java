package com.smartcampus.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.model.Assessment;
import com.smartcampus.model.enums.AssessmentType;

/** JDBC queries for assessments. */
@Repository
public class AssessmentRepository extends JdbcSupport {

    private static final String BASE_SELECT = """
            SELECT a.id, a.course_id, c.course_code, c.course_name, c.department, c.semester,
                   a.title, a.assessment_type, a.term, a.max_score, a.weight,
                   a.assessment_date, a.published, a.created_by_faculty_id,
                   f.name AS created_by_name, a.created_at, a.updated_at
            FROM assessments a
            JOIN courses c ON c.id = a.course_id
            LEFT JOIN faculty f ON f.id = a.created_by_faculty_id """;

    private static final RowMapper<Assessment> ASSESSMENT_ROW = (rs, rowNum) -> {
        Assessment assessment = new Assessment();
        assessment.setId(rs.getLong("id"));
        assessment.setCourseId(rs.getLong("course_id"));
        assessment.setCourseCode(str(rs, "course_code"));
        assessment.setCourseName(str(rs, "course_name"));
        assessment.setDepartment(str(rs, "department"));
        assessment.setSemester(intOf(rs, "semester"));
        assessment.setTitle(str(rs, "title"));
        assessment.setAssessmentType(enumOf(AssessmentType.class,
                str(rs, "assessment_type"), AssessmentType.OTHER));
        assessment.setTerm(str(rs, "term"));
        assessment.setMaxScore(decimal(rs, "max_score"));
        assessment.setWeight(nullableDecimal(rs, "weight"));
        assessment.setAssessmentDate(date(rs, "assessment_date"));
        assessment.setPublished(rs.getBoolean("published"));
        assessment.setCreatedByFacultyId(nullableLong(rs, "created_by_faculty_id"));
        assessment.setCreatedByName(str(rs, "created_by_name"));
        assessment.setCreatedAt(dateTime(rs, "created_at"));
        assessment.setUpdatedAt(dateTime(rs, "updated_at"));
        return assessment;
    };

    public AssessmentRepository(DataSource dataSource) {
        super(dataSource);
    }

    public Assessment save(Assessment assessment) {
        String sql = """
                INSERT INTO assessments
                    (course_id, title, assessment_type, term, max_score, weight,
                     assessment_date, published, created_by_faculty_id)
                VALUES (?,?,?,?,?,?,?,?,?)""";
        long id = insert(sql,
                assessment.getCourseId(), assessment.getTitle(), assessment.getAssessmentType(),
                assessment.getTerm(), assessment.getMaxScore(), assessment.getWeight(),
                assessment.getAssessmentDate(), assessment.isPublished(),
                assessment.getCreatedByFacultyId());
        assessment.setId(id);
        return assessment;
    }

    public Assessment update(Assessment assessment) {
        String sql = """
                UPDATE assessments SET
                    title = ?, assessment_type = ?, term = ?, max_score = ?, weight = ?,
                    assessment_date = ?, published = ?
                WHERE id = ?""";
        int affected = update(sql,
                assessment.getTitle(), assessment.getAssessmentType(), assessment.getTerm(),
                assessment.getMaxScore(), assessment.getWeight(), assessment.getAssessmentDate(),
                assessment.isPublished(), assessment.getId());
        if (affected == 0) {
            throw new com.smartcampus.exception.SmartCampusException("Assessment not found.");
        }
        return assessment;
    }

    public Optional<Assessment> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE a.id = ?", ASSESSMENT_ROW, id);
    }

    public List<Assessment> findByCourse(Long courseId, boolean publishedOnly) {
        String sql = BASE_SELECT + " WHERE a.course_id = ?"
                + (publishedOnly ? " AND a.published = TRUE" : "")
                + " ORDER BY a.assessment_date DESC, a.id DESC";
        return query(sql, ASSESSMENT_ROW, courseId);
    }

    public List<Assessment> findByCourseIds(List<Long> courseIds, boolean publishedOnly) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> params = new ArrayList<>(courseIds);
        String sql = BASE_SELECT + " WHERE a.course_id IN (" + placeholders(courseIds.size()) + ")"
                + (publishedOnly ? " AND a.published = TRUE" : "")
                + " ORDER BY c.course_code, a.assessment_date DESC, a.id DESC";
        return query(sql, ASSESSMENT_ROW, params.toArray());
    }

    public boolean existsByCourseTitleTerm(Long courseId, String title, String term, Long excludeId) {
        String sql = "SELECT COUNT(*) FROM assessments"
                + " WHERE course_id = ? AND title = ? AND term = ?"
                + (excludeId == null ? "" : " AND id <> ?");
        if (excludeId == null) {
            return exists(sql, courseId, title, term);
        }
        return exists(sql, courseId, title, term, excludeId);
    }

    public boolean setPublished(Long id, boolean published) {
        return update("UPDATE assessments SET published = ? WHERE id = ?", published, id) > 0;
    }

    public boolean deleteById(Long id) {
        return update("DELETE FROM assessments WHERE id = ?", id) > 0;
    }

    public long countByCourse(Long courseId) {
        return queryForLong("SELECT COUNT(*) FROM assessments WHERE course_id = ?", courseId);
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
