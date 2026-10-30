package com.smartcampus.repository;

import java.math.BigDecimal;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.model.StudentMark;
import com.smartcampus.model.enums.AssessmentType;

/** JDBC queries for student marks. */
@Repository
public class MarkRepository extends JdbcSupport {

    private static final String BASE_SELECT = """
            SELECT m.id, m.assessment_id, m.student_id, s.name AS student_name,
                   s.usn AS student_usn, s.section,
                   a.course_id, c.course_code, c.course_name, c.semester,
                   a.title, a.assessment_type, a.term, a.max_score, a.assessment_date,
                   a.published, m.score, m.remarks, m.entered_by_faculty_id,
                   f.name AS entered_by_name, m.entered_at, m.updated_at
            FROM student_marks m
            JOIN assessments a ON a.id = m.assessment_id
            JOIN courses c ON c.id = a.course_id
            JOIN students s ON s.id = m.student_id
            LEFT JOIN faculty f ON f.id = m.entered_by_faculty_id """;

    private static final RowMapper<StudentMark> MARK_ROW = (rs, rowNum) -> {
        StudentMark mark = new StudentMark();
        mark.setId(rs.getLong("id"));
        mark.setAssessmentId(rs.getLong("assessment_id"));
        mark.setStudentId(rs.getLong("student_id"));
        mark.setStudentName(str(rs, "student_name"));
        mark.setStudentUsn(str(rs, "student_usn"));
        mark.setSection(str(rs, "section"));
        mark.setCourseId(rs.getLong("course_id"));
        mark.setCourseCode(str(rs, "course_code"));
        mark.setCourseName(str(rs, "course_name"));
        mark.setSemester(intOf(rs, "semester"));
        mark.setTitle(str(rs, "title"));
        mark.setAssessmentType(enumOf(AssessmentType.class,
                str(rs, "assessment_type"), AssessmentType.OTHER));
        mark.setTerm(str(rs, "term"));
        mark.setMaxScore(decimal(rs, "max_score"));
        mark.setAssessmentDate(date(rs, "assessment_date"));
        mark.setPublished(rs.getBoolean("published"));
        mark.setScore(nullableDecimal(rs, "score"));
        mark.setRemarks(str(rs, "remarks"));
        mark.setEnteredByFacultyId(nullableLong(rs, "entered_by_faculty_id"));
        mark.setEnteredByName(str(rs, "entered_by_name"));
        mark.setEnteredAt(dateTime(rs, "entered_at"));
        mark.setUpdatedAt(dateTime(rs, "updated_at"));
        mark.setCreatedAt(dateTime(rs, "entered_at"));
        return mark;
    };

    public MarkRepository(DataSource dataSource) {
        super(dataSource);
    }

    public List<StudentMark> findByAssessment(Long assessmentId) {
        return query(BASE_SELECT + " WHERE m.assessment_id = ? ORDER BY s.name ASC",
                MARK_ROW, assessmentId);
    }

    public List<StudentMark> findByStudent(Long studentId, boolean publishedOnly) {
        String sql = BASE_SELECT + " WHERE m.student_id = ?"
                + (publishedOnly ? " AND a.published = TRUE" : "")
                + " ORDER BY c.course_code, a.assessment_date DESC, a.id DESC";
        return query(sql, MARK_ROW, studentId);
    }

    public List<StudentMark> findByStudentAndCourse(Long studentId, Long courseId,
                                                     boolean publishedOnly) {
        String sql = BASE_SELECT + " WHERE m.student_id = ? AND a.course_id = ?"
                + (publishedOnly ? " AND a.published = TRUE" : "")
                + " ORDER BY a.assessment_date DESC, a.id DESC";
        return query(sql, MARK_ROW, studentId, courseId);
    }

    /**
     * Insert or update one mark.  The unique key is (assessment_id, student_id),
     * so saving the same sheet twice corrects the existing row instead of
     * creating duplicates.
     */
    public void upsert(Long assessmentId, Long studentId, BigDecimal score,
                       String remarks, Long facultyId) {
        String sql = """
                INSERT INTO student_marks
                    (assessment_id, student_id, score, remarks, entered_by_faculty_id)
                VALUES (?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    score = VALUES(score),
                    remarks = VALUES(remarks),
                    entered_by_faculty_id = VALUES(entered_by_faculty_id)""";
        update(sql, assessmentId, studentId, score, remarks, facultyId);
    }

    public boolean deleteByAssessment(Long assessmentId) {
        return update("DELETE FROM student_marks WHERE assessment_id = ?", assessmentId) > 0;
    }
}
