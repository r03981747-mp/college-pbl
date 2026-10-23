package com.smartcampus.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.FacultyNotFoundException;
import com.smartcampus.model.Faculty;
import com.smartcampus.model.enums.FacultyStatus;
import com.smartcampus.utility.SqlCriteria;

/**
 * JDBC implementation of every faculty query (PreparedStatement only).
 */
@Repository
public class FacultyRepository extends JdbcSupport
        implements GenericRepository<Faculty, Long>, SearchableRepository<Faculty> {

    private static final String BASE_SELECT = """
            SELECT id, employee_id, name, email, phone, department, designation,
                   qualification, joining_date, status, user_id, created_at
            FROM faculty """;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "name", "name",
            "employeeId", "employee_id",
            "email", "email",
            "department", "department",
            "designation", "designation",
            "joiningDate", "joining_date",
            "status", "status",
            "id", "id");

    /** Lambda row mapper. */
    private static final RowMapper<Faculty> FACULTY_ROW = (rs, rowNum) -> {
        Faculty faculty = new Faculty(
                rs.getLong("id"),
                str(rs, "employee_id"),
                str(rs, "name"));
        faculty.setEmail(str(rs, "email"));
        faculty.setPhone(str(rs, "phone"));
        faculty.setDepartment(str(rs, "department"));
        faculty.setDesignation(str(rs, "designation"));
        faculty.setQualification(str(rs, "qualification"));
        faculty.setJoiningDate(date(rs, "joining_date"));
        faculty.setStatus(enumOf(FacultyStatus.class, str(rs, "status"), FacultyStatus.ACTIVE));
        faculty.setUserId(nullableLong(rs, "user_id"));
        faculty.setCreatedAt(dateTime(rs, "created_at"));
        return faculty;
    };

    public FacultyRepository(DataSource dataSource) {
        super(dataSource);
    }

    // ==================================================================
    //  GenericRepository
    // ==================================================================

    @Override
    public Faculty save(Faculty faculty) {
        String sql = """
                INSERT INTO faculty
                    (employee_id, name, email, phone, department, designation,
                     qualification, joining_date, status, user_id)
                VALUES (?,?,?,?,?,?,?,?,?,?)""";

        long id = insert(sql,
                faculty.getEmployeeId(), faculty.getName(), faculty.getEmail(),
                faculty.getPhone(), faculty.getDepartment(), faculty.getDesignation(),
                faculty.getQualification(), faculty.getJoiningDate(),
                faculty.getStatus(), faculty.getUserId());

        faculty.setId(id);
        return faculty;
    }

    @Override
    public Optional<Faculty> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE id = ?", FACULTY_ROW, id);
    }

    @Override
    public List<Faculty> findAll() {
        return query(BASE_SELECT + " ORDER BY name ASC", FACULTY_ROW);
    }

    @Override
    public Faculty update(Faculty faculty) {
        String sql = """
                UPDATE faculty SET
                    employee_id=?, name=?, email=?, phone=?, department=?, designation=?,
                    qualification=?, joining_date=?, status=?, user_id=?
                WHERE id=?""";

        int affected = update(sql,
                faculty.getEmployeeId(), faculty.getName(), faculty.getEmail(),
                faculty.getPhone(), faculty.getDepartment(), faculty.getDesignation(),
                faculty.getQualification(), faculty.getJoiningDate(),
                faculty.getStatus(), faculty.getUserId(), faculty.getId());

        if (affected == 0) {
            throw new FacultyNotFoundException(faculty.getId());
        }
        return faculty;
    }

    @Override
    public boolean deleteById(Long id) {
        return update("DELETE FROM faculty WHERE id = ?", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong("SELECT COUNT(*) FROM faculty");
    }

    @Override
    public boolean existsById(Long id) {
        return exists("SELECT COUNT(*) FROM faculty WHERE id = ?", id);
    }

    // ==================================================================
    //  SearchableRepository
    // ==================================================================

    @Override
    public List<Faculty> search(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);

        String sql = BASE_SELECT
                + where.getWhere() + " "
                + SqlCriteria.orderBy(criteria.getSort(), SORT_COLUMNS, "name") + " "
                + SqlCriteria.direction(criteria.getDirection())
                + " LIMIT ?, ?";

        List<Object> params = new ArrayList<>(where.getParams());
        params.add(criteria.offset());
        params.add(criteria.getSize());

        return query(sql, FACULTY_ROW, params.toArray());
    }

    @Override
    public long count(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);
        return queryForLong("SELECT COUNT(*) FROM faculty " + where.getWhere(),
                where.getParamsArray());
    }

    private SqlCriteria buildFilter(SearchCriteria criteria) {
        SqlCriteria where = new SqlCriteria();

        if (criteria.hasKeyword()) {
            String like = "%" + criteria.getKeyword().toLowerCase() + "%";
            where.and("(LOWER(employee_id) LIKE ? OR LOWER(name) LIKE ?"
                    + " OR LOWER(email) LIKE ? OR LOWER(department) LIKE ?"
                    + " OR LOWER(designation) LIKE ?)",
                    like, like, like, like, like);
        }

        where.andNotBlank(criteria.filter("department"), "department = ?");
        where.andNotBlank(criteria.filter("designation"), "designation = ?");
        where.andNotBlank(criteria.filter("status"), "status = ?");

        return where;
    }

    // ==================================================================
    //  Extra look-ups
    // ==================================================================

    public boolean existsByEmployeeId(String employeeId, Long excludeId) {
        SqlCriteria where = new SqlCriteria().and("employee_id = ?", employeeId);
        if (excludeId != null) {
            where.and("id <> ?", excludeId);
        }
        return exists("SELECT COUNT(*) FROM faculty " + where.getWhere(),
                where.getParamsArray());
    }

    public boolean existsByEmail(String email, Long excludeId) {
        SqlCriteria where = new SqlCriteria().and("email = ?", email);
        if (excludeId != null) {
            where.and("id <> ?", excludeId);
        }
        return exists("SELECT COUNT(*) FROM faculty " + where.getWhere(),
                where.getParamsArray());
    }

    public Optional<Faculty> findByUserId(Long userId) {
        return queryOne(BASE_SELECT + " WHERE user_id = ?", FACULTY_ROW, userId);
    }

    public List<String> findDistinctDepartments() {
        return query("SELECT DISTINCT department FROM faculty ORDER BY department",
                (rs, rowNum) -> str(rs, "department"));
    }

    public List<String> findDistinctDesignations() {
        return query("SELECT DISTINCT designation FROM faculty ORDER BY designation",
                (rs, rowNum) -> str(rs, "designation"));
    }
}
