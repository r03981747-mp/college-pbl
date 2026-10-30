package com.smartcampus.repository;

import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.model.AttendanceRecord;
import com.smartcampus.model.enums.AttendanceStatus;

/** JDBC queries for daily attendance. */
@Repository
public class AttendanceRepository extends JdbcSupport {

    private static final String BASE_SELECT = """
            SELECT a.id, a.student_id, s.name AS student_name, s.usn AS student_usn, s.section,
                   a.course_id, c.course_code, c.course_name, c.semester,
                   a.attendance_date, a.status, a.remarks,
                   a.marked_by_faculty_id, mf.name AS marked_by_name, a.marked_at,
                   a.updated_by_faculty_id, uf.name AS updated_by_name, a.updated_at
            FROM attendance a
            JOIN students s ON s.id = a.student_id
            JOIN courses c ON c.id = a.course_id
            LEFT JOIN faculty mf ON mf.id = a.marked_by_faculty_id
            LEFT JOIN faculty uf ON uf.id = a.updated_by_faculty_id """;

    private static final RowMapper<AttendanceRecord> ATTENDANCE_ROW = (rs, rowNum) -> {
        AttendanceRecord record = new AttendanceRecord();
        record.setId(rs.getLong("id"));
        record.setStudentId(rs.getLong("student_id"));
        record.setStudentName(str(rs, "student_name"));
        record.setStudentUsn(str(rs, "student_usn"));
        record.setSection(str(rs, "section"));
        record.setCourseId(rs.getLong("course_id"));
        record.setCourseCode(str(rs, "course_code"));
        record.setCourseName(str(rs, "course_name"));
        record.setSemester(intOf(rs, "semester"));
        record.setAttendanceDate(date(rs, "attendance_date"));
        record.setStatus(enumOf(AttendanceStatus.class,
                str(rs, "status"), AttendanceStatus.ABSENT));
        record.setRemarks(str(rs, "remarks"));
        record.setMarkedByFacultyId(nullableLong(rs, "marked_by_faculty_id"));
        record.setMarkedByName(str(rs, "marked_by_name"));
        record.setMarkedAt(dateTime(rs, "marked_at"));
        record.setUpdatedByFacultyId(nullableLong(rs, "updated_by_faculty_id"));
        record.setUpdatedByName(str(rs, "updated_by_name"));
        record.setUpdatedAt(dateTime(rs, "updated_at"));
        record.setCreatedAt(dateTime(rs, "marked_at"));
        return record;
    };

    public AttendanceRepository(DataSource dataSource) {
        super(dataSource);
    }

    public List<AttendanceRecord> findByCourseAndDate(Long courseId, LocalDate date) {
        return query(BASE_SELECT
                        + " WHERE a.course_id = ? AND a.attendance_date = ?"
                        + " ORDER BY s.name ASC",
                ATTENDANCE_ROW, courseId, date);
    }

    public List<AttendanceRecord> findByStudent(Long studentId) {
        return query(BASE_SELECT + " WHERE a.student_id = ?"
                        + " ORDER BY c.course_code, a.attendance_date DESC, a.id DESC",
                ATTENDANCE_ROW, studentId);
    }

    public List<AttendanceRecord> findByStudentAndCourse(Long studentId, Long courseId) {
        return query(BASE_SELECT
                        + " WHERE a.student_id = ? AND a.course_id = ?"
                        + " ORDER BY a.attendance_date DESC, a.id DESC",
                ATTENDANCE_ROW, studentId, courseId);
    }

    /**
     * Insert or correct one attendance row.  The unique key prevents duplicate
     * registers for the same student/course/date.
     */
    public void upsert(Long studentId, Long courseId, LocalDate date,
                       AttendanceStatus status, String remarks, Long facultyId) {
        String sql = """
                INSERT INTO attendance
                    (student_id, course_id, attendance_date, status, remarks,
                     marked_by_faculty_id, updated_by_faculty_id)
                VALUES (?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    status = VALUES(status),
                    remarks = VALUES(remarks),
                    updated_by_faculty_id = VALUES(updated_by_faculty_id)""";
        update(sql, studentId, courseId, date, status, remarks, facultyId, facultyId);
    }

    public long countByStudent(Long studentId) {
        return queryForLong("SELECT COUNT(*) FROM attendance WHERE student_id = ?", studentId);
    }
}
