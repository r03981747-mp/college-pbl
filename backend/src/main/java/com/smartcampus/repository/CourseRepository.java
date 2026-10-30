package com.smartcampus.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.CourseNotFoundException;
import com.smartcampus.model.Course;
import com.smartcampus.model.enums.CourseStatus;
import com.smartcampus.utility.SqlCriteria;

/**
 * JDBC implementation of every course query.
 *
 * <p>Note the {@code LEFT JOIN faculty} - a course may have no teacher yet,
 * but when one is assigned we return their name in the same round trip so the
 * UI does not need a second request.</p>
 */
@Repository
public class CourseRepository extends JdbcSupport
        implements GenericRepository<Course, Long>, SearchableRepository<Course> {

    private static final String BASE_SELECT = """
            SELECT c.id, c.course_code, c.course_name, c.department, c.semester,
                   c.credits, c.faculty_id, c.description, c.status, c.created_at,
                   f.name AS faculty_name
            FROM courses c
            LEFT JOIN faculty f ON f.id = c.faculty_id """;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "courseCode", "c.course_code",
            "courseName", "c.course_name",
            "department", "c.department",
            "semester", "c.semester",
            "credits", "c.credits",
            "status", "c.status",
            "facultyName", "f.name",
            "id", "c.id");

    /** Lambda row mapper. */
    private static final RowMapper<Course> COURSE_ROW = (rs, rowNum) -> {
        Course course = new Course(
                rs.getLong("id"),
                str(rs, "course_code"),
                str(rs, "course_name"));
        course.setDepartment(str(rs, "department"));
        course.setSemester(intOf(rs, "semester"));
        course.setCredits(intOf(rs, "credits"));
        course.setFacultyId(nullableLong(rs, "faculty_id"));
        course.setFacultyName(str(rs, "faculty_name"));
        course.setDescription(str(rs, "description"));
        course.setStatus(enumOf(CourseStatus.class, str(rs, "status"), CourseStatus.ACTIVE));
        course.setCreatedAt(dateTime(rs, "created_at"));
        return course;
    };

    public CourseRepository(DataSource dataSource) {
        super(dataSource);
    }

    // ==================================================================
    //  GenericRepository
    // ==================================================================

    @Override
    public Course save(Course course) {
        String sql = """
                INSERT INTO courses
                    (course_code, course_name, department, semester, credits,
                     faculty_id, description, status)
                VALUES (?,?,?,?,?,?,?,?)""";

        long id = insert(sql,
                course.getCourseCode(), course.getCourseName(), course.getDepartment(),
                course.getSemester(), course.getCredits(), course.getFacultyId(),
                course.getDescription(), course.getStatus());

        course.setId(id);
        return course;
    }

    @Override
    public Optional<Course> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE c.id = ?", COURSE_ROW, id);
    }

    @Override
    public List<Course> findAll() {
        return query(BASE_SELECT + " ORDER BY c.course_code ASC", COURSE_ROW);
    }

    @Override
    public Course update(Course course) {
        String sql = """
                UPDATE courses SET
                    course_code=?, course_name=?, department=?, semester=?, credits=?,
                    faculty_id=?, description=?, status=?
                WHERE id=?""";

        int affected = update(sql,
                course.getCourseCode(), course.getCourseName(), course.getDepartment(),
                course.getSemester(), course.getCredits(), course.getFacultyId(),
                course.getDescription(), course.getStatus(), course.getId());

        if (affected == 0) {
            throw new CourseNotFoundException(course.getId());
        }
        return course;
    }

    @Override
    public boolean deleteById(Long id) {
        return update("DELETE FROM courses WHERE id = ?", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong("SELECT COUNT(*) FROM courses");
    }

    @Override
    public boolean existsById(Long id) {
        return exists("SELECT COUNT(*) FROM courses WHERE id = ?", id);
    }

    // ==================================================================
    //  SearchableRepository
    // ==================================================================

    @Override
    public List<Course> search(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);

        String sql = BASE_SELECT
                + where.getWhere() + " "
                + SqlCriteria.orderBy(criteria.getSort(), SORT_COLUMNS, "c.course_code") + " "
                + SqlCriteria.direction(criteria.getDirection())
                + " LIMIT ?, ?";

        List<Object> params = new ArrayList<>(where.getParams());
        params.add(criteria.offset());
        params.add(criteria.getSize());

        return query(sql, COURSE_ROW, params.toArray());
    }

    @Override
    public long count(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);
        return queryForLong("SELECT COUNT(*) FROM courses c "
                + "LEFT JOIN faculty f ON f.id = c.faculty_id "
                + where.getWhere(), where.getParamsArray());
    }

    private SqlCriteria buildFilter(SearchCriteria criteria) {
        SqlCriteria where = new SqlCriteria();

        if (criteria.hasKeyword()) {
            String like = "%" + criteria.getKeyword().toLowerCase() + "%";
            where.and("(LOWER(c.course_code) LIKE ? OR LOWER(c.course_name) LIKE ?"
                    + " OR LOWER(c.department) LIKE ? OR LOWER(f.name) LIKE ?)",
                    like, like, like, like);
        }

        where.andNotBlank(criteria.filter("department"), "c.department = ?");
        where.andNumeric(criteria.filter("semester"), "c.semester = ?");
        where.andNotBlank(criteria.filter("status"), "c.status = ?");
        where.andNumeric(criteria.filter("facultyId"), "c.faculty_id = ?");

        return where;
    }

    // ==================================================================
    //  Extra look-ups
    // ==================================================================

    public boolean existsByCourseCode(String courseCode, Long excludeId) {
        SqlCriteria where = new SqlCriteria().and("course_code = ?", courseCode);
        if (excludeId != null) {
            where.and("id <> ?", excludeId);
        }
        return exists("SELECT COUNT(*) FROM courses " + where.getWhere(),
                where.getParamsArray());
    }

    /** Assign (or clear) the teacher of a course. */
    public boolean assignFaculty(Long courseId, Long facultyId) {
        if (facultyId == null) {
            return update("UPDATE courses SET faculty_id = NULL WHERE id = ?", courseId) > 0;
        }
        return update("UPDATE courses SET faculty_id = ? WHERE id = ?", facultyId, courseId) > 0;
    }

    public List<String> findDistinctDepartments() {
        return query("SELECT DISTINCT department FROM courses ORDER BY department",
                (rs, rowNum) -> str(rs, "department"));
    }

    /** Courses that still have no teacher assigned (used by the reports page). */
    public List<Course> findUnassignedCourses() {
        return query(BASE_SELECT + " WHERE c.faculty_id IS NULL ORDER BY c.semester, c.course_code",
                COURSE_ROW);
    }

    // ==================================================================
    //  Academic module look-ups
    // ==================================================================

    public List<Course> findAllActive() {
        return query(BASE_SELECT + " WHERE c.status = 'ACTIVE' ORDER BY c.semester, c.course_code",
                COURSE_ROW);
    }

    public List<Course> findActiveByFacultyId(Long facultyId) {
        return query(BASE_SELECT
                        + " WHERE c.faculty_id = ? AND c.status = 'ACTIVE'"
                        + " ORDER BY c.semester, c.course_code",
                COURSE_ROW, facultyId);
    }

    public List<Course> findActiveByDepartmentAndSemester(String department, int semester) {
        return query(BASE_SELECT
                        + " WHERE c.department = ? AND c.semester = ? AND c.status = 'ACTIVE'"
                        + " ORDER BY c.course_code",
                COURSE_ROW, department, semester);
    }

    /**
     * True when the faculty member teaches at least one active subject in the
     * student's department + semester.  This is the implicit enrollment rule
     * used throughout the academic module.
     */
    public boolean existsActiveForFacultyAndClass(Long facultyId, String department, int semester) {
        return exists("""
                SELECT COUNT(*) FROM courses c
                WHERE c.faculty_id = ?
                  AND c.department = ?
                  AND c.semester = ?
                  AND c.status = 'ACTIVE'""", facultyId, department, semester);
    }
}
