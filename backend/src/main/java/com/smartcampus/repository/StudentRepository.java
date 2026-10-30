package com.smartcampus.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.model.Student;
import com.smartcampus.utility.SqlCriteria;

/**
 * JDBC implementation of every student query.
 *
 * <p>All SQL uses {@link java.sql.PreparedStatement} placeholders - the
 * keyword, filters, sort direction and paging values are all bound, never
 * concatenated, so the search box cannot be used for SQL injection.</p>
 *
 * <p><b>Flow:</b> {@code Controller -> Service -> this repository -> MySQL}.</p>
 */
@Repository
public class StudentRepository extends JdbcSupport
        implements GenericRepository<Student, Long>, SearchableRepository<Student> {

    private static final String BASE_SELECT = """
            SELECT s.id, s.usn, s.name, s.email, s.phone, s.gender, s.date_of_birth,
                   s.department, s.semester, s.section, s.address, s.admission_year,
                   s.status, s.user_id, s.class_teacher_id, s.mentor_id,
                   ct.name AS class_teacher_name, m.name AS mentor_name, s.created_at
            FROM students s
            LEFT JOIN faculty ct ON ct.id = s.class_teacher_id
            LEFT JOIN faculty m ON m.id = s.mentor_id """;

    /** Whitelist mapping UI sort keys to real database columns. */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "name", "s.name",
            "usn", "s.usn",
            "email", "s.email",
            "department", "s.department",
            "semester", "s.semester",
            "admissionYear", "s.admission_year",
            "status", "s.status",
            "id", "s.id");

    /**
     * Row mapper written as a LAMBDA EXPRESSION.
     * {@code (ResultSet, int) -> Student}
     */
    private static final RowMapper<Student> STUDENT_ROW = (rs, rowNum) -> {
        Student student = new Student(
                rs.getLong("id"),
                str(rs, "usn"),
                str(rs, "name"));
        student.setEmail(str(rs, "email"));
        student.setPhone(str(rs, "phone"));
        student.setGender(enumOf(com.smartcampus.model.enums.Gender.class,
                str(rs, "gender"), null));
        student.setDateOfBirth(date(rs, "date_of_birth"));
        student.setDepartment(str(rs, "department"));
        student.setSemester(intOf(rs, "semester"));
        student.setSection(str(rs, "section"));
        student.setAddress(str(rs, "address"));
        student.setAdmissionYear(intOf(rs, "admission_year"));
        student.setStatus(enumOf(com.smartcampus.model.enums.StudentStatus.class,
                str(rs, "status"), com.smartcampus.model.enums.StudentStatus.ACTIVE));
        student.setUserId(nullableLong(rs, "user_id"));
        student.setClassTeacherId(nullableLong(rs, "class_teacher_id"));
        student.setClassTeacherName(str(rs, "class_teacher_name"));
        student.setMentorId(nullableLong(rs, "mentor_id"));
        student.setMentorName(str(rs, "mentor_name"));
        student.setCreatedAt(dateTime(rs, "created_at"));
        return student;
    };

    public StudentRepository(DataSource dataSource) {
        super(dataSource);
    }

    // ==================================================================
    //  GenericRepository implementation
    // ==================================================================

    @Override
    public Student save(Student student) {
        String sql = """
                INSERT INTO students
                    (usn, name, email, phone, gender, date_of_birth, department,
                     semester, section, address, admission_year, status, user_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""";

        long id = insert(sql,
                student.getUsn(), student.getName(), student.getEmail(), student.getPhone(),
                student.getGender(), student.getDateOfBirth(), student.getDepartment(),
                student.getSemester(), student.getSection(), student.getAddress(),
                student.getAdmissionYear(), student.getStatus(), student.getUserId());

        student.setId(id);
        return student;
    }

    @Override
    public Optional<Student> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE s.id = ?", STUDENT_ROW, id);
    }

    @Override
    public List<Student> findAll() {
        return query(BASE_SELECT + " ORDER BY s.name ASC", STUDENT_ROW);
    }

    @Override
    public Student update(Student student) {
        String sql = """
                UPDATE students SET
                    usn=?, name=?, email=?, phone=?, gender=?, date_of_birth=?,
                    department=?, semester=?, section=?, address=?, admission_year=?,
                    status=?, user_id=?
                WHERE id=?""";

        int affected = update(sql,
                student.getUsn(), student.getName(), student.getEmail(), student.getPhone(),
                student.getGender(), student.getDateOfBirth(), student.getDepartment(),
                student.getSemester(), student.getSection(), student.getAddress(),
                student.getAdmissionYear(), student.getStatus(), student.getUserId(),
                student.getId());

        if (affected == 0) {
            throw new com.smartcampus.exception.StudentNotFoundException(student.getId());
        }
        return student;
    }

    @Override
    public boolean deleteById(Long id) {
        return update("DELETE FROM students WHERE id = ?", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong("SELECT COUNT(*) FROM students");
    }

    @Override
    public boolean existsById(Long id) {
        return exists("SELECT COUNT(*) FROM students WHERE id = ?", id);
    }

    // ==================================================================
    //  SearchableRepository implementation
    // ==================================================================

    @Override
    public List<Student> search(SearchCriteria criteria) {
        return search(criteria, null);
    }

    /**
     * Faculty-scoped search.  A null {@code facultyId} means the caller is an
     * administrator and may see every student.
     */
    public List<Student> search(SearchCriteria criteria, Long facultyId) {
        SqlCriteria where = buildFilter(criteria, facultyId);

        String sql = BASE_SELECT
                + where.getWhere() + " "
                + SqlCriteria.orderBy(criteria.getSort(), SORT_COLUMNS, "s.name") + " "
                + SqlCriteria.direction(criteria.getDirection())
                + " LIMIT ?, ?";

        List<Object> params = new ArrayList<>(where.getParams());
        params.add(criteria.offset());
        params.add(criteria.getSize());

        return query(sql, STUDENT_ROW, params.toArray());
    }

    @Override
    public long count(SearchCriteria criteria) {
        return count(criteria, null);
    }

    public long count(SearchCriteria criteria, Long facultyId) {
        SqlCriteria where = buildFilter(criteria, facultyId);
        return queryForLong("SELECT COUNT(*) FROM students s " + where.getWhere(),
                where.getParamsArray());
    }

    /** Keyword + structured filters, all bound as PreparedStatement parameters. */
    private SqlCriteria buildFilter(SearchCriteria criteria, Long facultyId) {
        SqlCriteria where = new SqlCriteria();

        if (criteria.hasKeyword()) {
            String like = "%" + criteria.getKeyword().toLowerCase() + "%";
            where.and("(LOWER(s.usn) LIKE ? OR LOWER(s.name) LIKE ?"
                    + " OR LOWER(s.email) LIKE ? OR LOWER(s.department) LIKE ?)",
                    like, like, like, like);
        }

        where.andNotBlank(criteria.filter("department"), "s.department = ?");
        where.andNumeric(criteria.filter("semester"), "s.semester = ?");
        where.andNotBlank(criteria.filter("status"), "s.status = ?");
        where.andNotBlank(criteria.filter("section"), "s.section = ?");
        where.andNumeric(criteria.filter("admissionYear"), "s.admission_year = ?");

        if (facultyId != null) {
            // A faculty member sees a student only when they are the class
            // teacher/mentor or teach a subject in that student's class.
            where.and("""
                    (s.class_teacher_id = ? OR s.mentor_id = ?
                     OR EXISTS (
                         SELECT 1 FROM courses c
                         WHERE c.faculty_id = ?
                           AND c.department = s.department
                           AND c.semester = s.semester
                           AND c.status = 'ACTIVE'
                     ))""", facultyId, facultyId, facultyId);
        }

        return where;
    }

    // ==================================================================
    //  Extra look-ups used by the service layer
    // ==================================================================

    /** Unique-constraint check used before insert and update. */
    public boolean existsByUsn(String usn, Long excludeId) {
        SqlCriteria where = new SqlCriteria().and("usn = ?", usn);
        if (excludeId != null) {
            where.and("id <> ?", excludeId);
        }
        return exists("SELECT COUNT(*) FROM students " + where.getWhere(),
                where.getParamsArray());
    }

    /** Unique-constraint check on the e-mail address. */
    public boolean existsByEmail(String email, Long excludeId) {
        SqlCriteria where = new SqlCriteria().and("email = ?", email);
        if (excludeId != null) {
            where.and("id <> ?", excludeId);
        }
        return exists("SELECT COUNT(*) FROM students " + where.getWhere(),
                where.getParamsArray());
    }

    /** Looks up the student profile linked to a login account (own-profile view). */
    public Optional<Student> findByUserId(Long userId) {
        return queryOne(BASE_SELECT + " WHERE s.user_id = ?", STUDENT_ROW, userId);
    }

    /** Unique lookup by the university seat number. */
    public Optional<Student> findByUsn(String usn) {
        return queryOne(BASE_SELECT + " WHERE s.usn = ?", STUDENT_ROW, usn);
    }

    /** Case-insensitive USN search used by the type-ahead box. */
    public List<Student> findByUsnLike(String usn) {
        return query(BASE_SELECT + " WHERE LOWER(s.usn) LIKE ? ORDER BY s.usn LIMIT 20",
                STUDENT_ROW, "%" + usn.toLowerCase() + "%");
    }

    /** Departments present in the table - used to populate the filter dropdown. */
    public List<String> findDistinctDepartments() {
        return query("SELECT DISTINCT department FROM students ORDER BY department",
                (rs, rowNum) -> str(rs, "department"));
    }

    /** All active students (administrator views). */
    public List<Student> findAllActive() {
        return query(BASE_SELECT + " WHERE s.status = 'ACTIVE' ORDER BY s.name ASC", STUDENT_ROW);
    }

    /** Students implicitly enrolled in a course via department + semester. */
    public List<Student> findByDepartmentAndSemester(String department, int semester) {
        return query(BASE_SELECT
                        + " WHERE s.department = ? AND s.semester = ? AND s.status = 'ACTIVE'"
                        + " ORDER BY s.name ASC",
                STUDENT_ROW, department, semester);
    }

    /**
     * Faculty visibility rule used by the Students page and academic profile
     * picker: class teacher, mentor, or teacher of a subject in the student's
     * department + semester.
     */
    public List<Student> findAccessibleByFaculty(Long facultyId) {
        return query(BASE_SELECT
                        + " WHERE s.status = 'ACTIVE' AND ("
                        + "      s.class_teacher_id = ? OR s.mentor_id = ?"
                        + "      OR EXISTS ("
                        + "          SELECT 1 FROM courses c"
                        + "          WHERE c.faculty_id = ?"
                        + "            AND c.department = s.department"
                        + "            AND c.semester = s.semester"
                        + "            AND c.status = 'ACTIVE'"
                        + "      ))"
                        + " ORDER BY s.name ASC",
                STUDENT_ROW, facultyId, facultyId, facultyId);
    }

    /** Administrator assignment of class teacher and mentor. */
    public boolean assignClassTeacherMentor(Long studentId, Long classTeacherId, Long mentorId) {
        return update("UPDATE students SET class_teacher_id = ?, mentor_id = ? WHERE id = ?",
                classTeacherId, mentorId, studentId) > 0;
    }

    public long countActive() {
        return queryForLong("SELECT COUNT(*) FROM students WHERE status = 'ACTIVE'");
    }
}
