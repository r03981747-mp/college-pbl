package com.smartcampus.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.ResourceNotFoundException;
import com.smartcampus.model.Laboratory;
import com.smartcampus.model.enums.ResourceStatus;
import com.smartcampus.utility.SqlCriteria;

/** JDBC implementation of every laboratory query (PreparedStatement only). */
@Repository
public class LaboratoryRepository extends JdbcSupport
        implements GenericRepository<Laboratory, Long>, SearchableRepository<Laboratory> {

    private static final String BASE_SELECT = """
            SELECT id, lab_number, lab_name, building, department, capacity, lab_type,
                   equipment_count, status, description, created_at
            FROM laboratories """;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "labNumber", "lab_number",
            "labName", "lab_name",
            "building", "building",
            "department", "department",
            "capacity", "capacity",
            "equipmentCount", "equipment_count",
            "status", "status",
            "id", "id");

    /** Lambda row mapper. */
    private static final RowMapper<Laboratory> LABORATORY_ROW = (rs, rowNum) -> {
        Laboratory lab = new Laboratory(
                rs.getLong("id"),
                str(rs, "lab_number"));
        lab.setLabName(str(rs, "lab_name"));
        lab.setBuilding(str(rs, "building"));
        lab.setDepartment(str(rs, "department"));
        lab.setCapacity(intOf(rs, "capacity"));
        lab.setLabType(str(rs, "lab_type"));
        lab.setEquipmentCount(intOf(rs, "equipment_count"));
        lab.setStatus(enumOf(ResourceStatus.class, str(rs, "status"), ResourceStatus.ACTIVE));
        lab.setDescription(str(rs, "description"));
        lab.setCreatedAt(dateTime(rs, "created_at"));
        return lab;
    };

    public LaboratoryRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Laboratory save(Laboratory laboratory) {
        String sql = """
                INSERT INTO laboratories
                    (lab_number, lab_name, building, department, capacity, lab_type,
                     equipment_count, status, description)
                VALUES (?,?,?,?,?,?,?,?,?)""";

        long id = insert(sql,
                laboratory.getLabNumber(), laboratory.getLabName(), laboratory.getBuilding(),
                laboratory.getDepartment(), laboratory.getCapacity(), laboratory.getLabType(),
                laboratory.getEquipmentCount(), laboratory.getStatus(),
                laboratory.getDescription());

        laboratory.setId(id);
        return laboratory;
    }

    @Override
    public Optional<Laboratory> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE id = ?", LABORATORY_ROW, id);
    }

    @Override
    public List<Laboratory> findAll() {
        return query(BASE_SELECT + " ORDER BY lab_number ASC", LABORATORY_ROW);
    }

    @Override
    public Laboratory update(Laboratory laboratory) {
        String sql = """
                UPDATE laboratories SET
                    lab_number=?, lab_name=?, building=?, department=?, capacity=?,
                    lab_type=?, equipment_count=?, status=?, description=?
                WHERE id=?""";

        int affected = update(sql,
                laboratory.getLabNumber(), laboratory.getLabName(), laboratory.getBuilding(),
                laboratory.getDepartment(), laboratory.getCapacity(), laboratory.getLabType(),
                laboratory.getEquipmentCount(), laboratory.getStatus(),
                laboratory.getDescription(), laboratory.getId());

        if (affected == 0) {
            throw new ResourceNotFoundException(laboratory.getId());
        }
        return laboratory;
    }

    @Override
    public boolean deleteById(Long id) {
        return update("DELETE FROM laboratories WHERE id = ?", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong("SELECT COUNT(*) FROM laboratories");
    }

    @Override
    public boolean existsById(Long id) {
        return exists("SELECT COUNT(*) FROM laboratories WHERE id = ?", id);
    }

    @Override
    public List<Laboratory> search(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);

        String sql = BASE_SELECT
                + where.getWhere() + " "
                + SqlCriteria.orderBy(criteria.getSort(), SORT_COLUMNS, "lab_number") + " "
                + SqlCriteria.direction(criteria.getDirection())
                + " LIMIT ?, ?";

        List<Object> params = new ArrayList<>(where.getParams());
        params.add(criteria.offset());
        params.add(criteria.getSize());

        return query(sql, LABORATORY_ROW, params.toArray());
    }

    @Override
    public long count(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);
        return queryForLong("SELECT COUNT(*) FROM laboratories " + where.getWhere(),
                where.getParamsArray());
    }

    private SqlCriteria buildFilter(SearchCriteria criteria) {
        SqlCriteria where = new SqlCriteria();

        if (criteria.hasKeyword()) {
            String like = "%" + criteria.getKeyword().toLowerCase() + "%";
            where.and("(LOWER(lab_number) LIKE ? OR LOWER(lab_name) LIKE ?"
                    + " OR LOWER(building) LIKE ? OR LOWER(department) LIKE ?"
                    + " OR LOWER(lab_type) LIKE ?)", like, like, like, like, like);
        }

        where.andNotBlank(criteria.filter("department"), "department = ?");
        where.andNotBlank(criteria.filter("building"), "building = ?");
        where.andNotBlank(criteria.filter("labType"), "lab_type = ?");
        where.andNotBlank(criteria.filter("status"), "status = ?");

        return where;
    }

    // ==================================================================
    //  Extra look-ups
    // ==================================================================

    public boolean existsByLabNumber(String labNumber, Long excludeId) {
        SqlCriteria where = new SqlCriteria().and("lab_number = ?", labNumber);
        if (excludeId != null) {
            where.and("id <> ?", excludeId);
        }
        return exists("SELECT COUNT(*) FROM laboratories " + where.getWhere(),
                where.getParamsArray());
    }

    public Optional<Laboratory> findByLabNumber(String labNumber) {
        return queryOne(BASE_SELECT + " WHERE lab_number = ?", LABORATORY_ROW, labNumber);
    }

    public List<String> findDistinctDepartments() {
        return query("SELECT DISTINCT department FROM laboratories ORDER BY department",
                (rs, rowNum) -> str(rs, "department"));
    }

    public List<String> findDistinctLabTypes() {
        return query("SELECT DISTINCT lab_type FROM laboratories ORDER BY lab_type",
                (rs, rowNum) -> str(rs, "lab_type"));
    }
}
