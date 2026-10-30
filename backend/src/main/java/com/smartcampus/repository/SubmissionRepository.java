package com.smartcampus.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.model.AssignmentSubmission;

/** JDBC queries for secure assignment submissions. */
@Repository
public class SubmissionRepository extends JdbcSupport {

    private static final String BASE_SELECT = """
            SELECT s.id, s.assignment_id, a.title AS assignment_title, a.course_id,
                   c.course_code, c.course_name, s.student_id,
                   st.name AS student_name, st.usn AS student_usn,
                   s.original_file_name, s.stored_file_name, s.content_type, s.file_size,
                   s.submitted_at, s.late, s.attempt_no, s.status, s.grade,
                   a.max_score AS assignment_max_score, s.feedback,
                   s.reviewed_by_faculty_id, f.name AS reviewed_by_name,
                   s.reviewed_at, s.updated_at
            FROM assignment_submissions s
            JOIN assignments a ON a.id = s.assignment_id
            JOIN courses c ON c.id = a.course_id
            JOIN students st ON st.id = s.student_id
            LEFT JOIN faculty f ON f.id = s.reviewed_by_faculty_id """;

    private static final RowMapper<AssignmentSubmission> SUBMISSION_ROW = (rs, rowNum) -> {
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(rs.getLong("id"));
        submission.setAssignmentId(rs.getLong("assignment_id"));
        submission.setAssignmentTitle(str(rs, "assignment_title"));
        submission.setCourseId(rs.getLong("course_id"));
        submission.setCourseCode(str(rs, "course_code"));
        submission.setCourseName(str(rs, "course_name"));
        submission.setStudentId(rs.getLong("student_id"));
        submission.setStudentName(str(rs, "student_name"));
        submission.setStudentUsn(str(rs, "student_usn"));
        submission.setOriginalFileName(str(rs, "original_file_name"));
        submission.setStoredFileName(str(rs, "stored_file_name"));
        submission.setContentType(str(rs, "content_type"));
        submission.setFileSize(rs.getLong("file_size"));
        submission.setSubmittedAt(dateTime(rs, "submitted_at"));
        submission.setLate(rs.getBoolean("late"));
        submission.setAttemptNo(intOf(rs, "attempt_no"));
        submission.setStatus(str(rs, "status"));
        submission.setGrade(nullableDecimal(rs, "grade"));
        submission.setAssignmentMaxScore(decimal(rs, "assignment_max_score"));
        submission.setFeedback(str(rs, "feedback"));
        submission.setReviewedByFacultyId(nullableLong(rs, "reviewed_by_faculty_id"));
        submission.setReviewedByName(str(rs, "reviewed_by_name"));
        submission.setReviewedAt(dateTime(rs, "reviewed_at"));
        submission.setUpdatedAt(dateTime(rs, "updated_at"));
        submission.setCreatedAt(dateTime(rs, "submitted_at"));
        return submission;
    };

    public SubmissionRepository(DataSource dataSource) {
        super(dataSource);
    }

    public Optional<AssignmentSubmission> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE s.id = ?", SUBMISSION_ROW, id);
    }

    public Optional<AssignmentSubmission> findByAssignmentAndStudent(Long assignmentId,
                                                                      Long studentId) {
        return queryOne(BASE_SELECT
                        + " WHERE s.assignment_id = ? AND s.student_id = ?",
                SUBMISSION_ROW, assignmentId, studentId);
    }

    public List<AssignmentSubmission> findByAssignment(Long assignmentId) {
        return query(BASE_SELECT + " WHERE s.assignment_id = ? ORDER BY st.name ASC",
                SUBMISSION_ROW, assignmentId);
    }

    public List<AssignmentSubmission> findByStudent(Long studentId) {
        return query(BASE_SELECT + " WHERE s.student_id = ?"
                        + " ORDER BY s.submitted_at DESC, s.id DESC",
                SUBMISSION_ROW, studentId);
    }

    /**
     * Store a first submission or replace an allowed resubmission.  Review
     * fields are cleared on replacement because the new file has not been
     * reviewed yet.
     */
    public AssignmentSubmission saveOrReplace(Long assignmentId, Long studentId,
                                               String originalFileName, String storedFileName,
                                               String contentType, long fileSize,
                                               boolean late) {
        Optional<AssignmentSubmission> existing =
                findByAssignmentAndStudent(assignmentId, studentId);

        if (existing.isPresent()) {
            AssignmentSubmission old = existing.get();
            String sql = """
                    UPDATE assignment_submissions SET
                        original_file_name = ?, stored_file_name = ?, content_type = ?,
                        file_size = ?, submitted_at = CURRENT_TIMESTAMP, late = ?,
                        attempt_no = ?, status = ?, grade = NULL, feedback = NULL,
                        reviewed_by_faculty_id = NULL, reviewed_at = NULL
                    WHERE id = ?""";
            update(sql, originalFileName, storedFileName, contentType, fileSize, late,
                    old.getAttemptNo() + 1, late ? "LATE" : "SUBMITTED", old.getId());
            return findById(old.getId()).orElseThrow();
        }

        String sql = """
                INSERT INTO assignment_submissions
                    (assignment_id, student_id, original_file_name, stored_file_name,
                     content_type, file_size, late, attempt_no, status)
                VALUES (?,?,?,?,?,?,?,?,?)""";
        long id = insert(sql, assignmentId, studentId, originalFileName, storedFileName,
                contentType, fileSize, late, 1, late ? "LATE" : "SUBMITTED");
        return findById(id).orElseThrow();
    }

    public boolean review(Long submissionId, BigDecimal grade, String feedback, Long facultyId) {
        if (grade == null) {
            return update("""
                    UPDATE assignment_submissions SET
                        grade = NULL, feedback = ?, reviewed_by_faculty_id = ?,
                        reviewed_at = CURRENT_TIMESTAMP,
                        status = IF(late, 'LATE', 'SUBMITTED')
                    WHERE id = ?""", feedback, facultyId, submissionId) > 0;
        }
        return update("""
                UPDATE assignment_submissions SET
                    grade = ?, feedback = ?, reviewed_by_faculty_id = ?,
                    reviewed_at = CURRENT_TIMESTAMP, status = 'GRADED'
                WHERE id = ?""", grade, feedback, facultyId, submissionId) > 0;
    }

    public int countByAssignment(Long assignmentId) {
        return (int) queryForLong(
                "SELECT COUNT(*) FROM assignment_submissions WHERE assignment_id = ?",
                assignmentId);
    }

    public int countGradedByAssignment(Long assignmentId) {
        return (int) queryForLong(
                "SELECT COUNT(*) FROM assignment_submissions"
                        + " WHERE assignment_id = ? AND status = 'GRADED'",
                assignmentId);
    }

    public int deleteByAssignment(Long assignmentId) {
        return update("DELETE FROM assignment_submissions WHERE assignment_id = ?", assignmentId);
    }
}
