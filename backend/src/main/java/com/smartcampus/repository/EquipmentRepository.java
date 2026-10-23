package com.smartcampus.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.ResourceNotFoundException;
import com.smartcampus.model.Equipment;
import com.smartcampus.model.enums.EquipmentCondition;
import com.smartcampus.model.enums.ResourceStatus;
import com.smartcampus.utility.SqlCriteria;

/**
 * JDBC implementation of every equipment query.
 *
 * <p>The column name is {@code `condition`} - a MySQL reserved word, so it is
 * always back-quoted in the SQL while {@code rs.getString("condition")} works
 * normally on the result set.</p>
 */
@Repository
public class EquipmentRepository extends JdbcSupport
        implements GenericRepository<Equipment, Long>, SearchableRepository<Equipment> {

    private static final String BASE_SELECT = """
            SELECT id, equipment_name, category, serial_number, quantity, location,
                   purchase_date, `condition`, status, description, created_at
            FROM equipment """;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "equipmentName", "equipment_name",
            "category", "category",
            "serialNumber", "serial_number",
            "quantity", "quantity",
            "location", "location",
            "purchaseDate", "purchase_date",
            "condition", "`condition`",
            "status", "status",
            "id", "id");

    /** Lambda row mapper. */
    private static final RowMapper<Equipment> EQUIPMENT_ROW = (rs, rowNum) -> {
        Equipment equipment = new Equipment(
                rs.getLong("id"),
                str(rs, "serial_number"));
        equipment.setEquipmentName(str(rs, "equipment_name"));
        equipment.setCategory(str(rs, "category"));
        equipment.setQuantity(intOf(rs, "quantity"));
        equipment.setLocation(str(rs, "location"));
        equipment.setPurchaseDate(date(rs, "purchase_date"));
        equipment.setCondition(enumOf(EquipmentCondition.class,
                str(rs, "condition"), EquipmentCondition.NEW));
        equipment.setStatus(enumOf(ResourceStatus.class, str(rs, "status"), ResourceStatus.ACTIVE));
        equipment.setDescription(str(rs, "description"));
        equipment.setCreatedAt(dateTime(rs, "created_at"));
        return equipment;
    };

    public EquipmentRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Equipment save(Equipment equipment) {
        String sql = """
                INSERT INTO equipment
                    (equipment_name, category, serial_number, quantity, location,
                     purchase_date, `condition`, status, description)
                VALUES (?,?,?,?,?,?,?,?,?)""";

        long id = insert(sql,
                equipment.getEquipmentName(), equipment.getCategory(),
                equipment.getSerialNumber(), equipment.getQuantity(),
                equipment.getLocation(), equipment.getPurchaseDate(),
                equipment.getCondition(), equipment.getStatus(),
                equipment.getDescription());

        equipment.setId(id);
        return equipment;
    }

    @Override
    public Optional<Equipment> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE id = ?", EQUIPMENT_ROW, id);
    }

    @Override
    public List<Equipment> findAll() {
        return query(BASE_SELECT + " ORDER BY equipment_name ASC", EQUIPMENT_ROW);
    }

    @Override
    public Equipment update(Equipment equipment) {
        String sql = """
                UPDATE equipment SET
                    equipment_name=?, category=?, serial_number=?, quantity=?, location=?,
                    purchase_date=?, `condition`=?, status=?, description=?
                WHERE id=?""";

        int affected = update(sql,
                equipment.getEquipmentName(), equipment.getCategory(),
                equipment.getSerialNumber(), equipment.getQuantity(),
                equipment.getLocation(), equipment.getPurchaseDate(),
                equipment.getCondition(), equipment.getStatus(),
                equipment.getDescription(), equipment.getId());

        if (affected == 0) {
            throw new ResourceNotFoundException(equipment.getId());
        }
        return equipment;
    }

    @Override
    public boolean deleteById(Long id) {
        return update("DELETE FROM equipment WHERE id = ?", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong("SELECT COUNT(*) FROM equipment");
    }

    @Override
    public boolean existsById(Long id) {
        return exists("SELECT COUNT(*) FROM equipment WHERE id = ?", id);
    }

    @Override
    public List<Equipment> search(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);

        String sql = BASE_SELECT
                + where.getWhere() + " "
                + SqlCriteria.orderBy(criteria.getSort(), SORT_COLUMNS, "equipment_name") + " "
                + SqlCriteria.direction(criteria.getDirection())
                + " LIMIT ?, ?";

        List<Object> params = new ArrayList<>(where.getParams());
        params.add(criteria.offset());
        params.add(criteria.getSize());

        return query(sql, EQUIPMENT_ROW, params.toArray());
    }

    @Override
    public long count(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);
        return queryForLong("SELECT COUNT(*) FROM equipment " + where.getWhere(),
                where.getParamsArray());
    }

    private SqlCriteria buildFilter(SearchCriteria criteria) {
        SqlCriteria where = new SqlCriteria();

        if (criteria.hasKeyword()) {
            String like = "%" + criteria.getKeyword().toLowerCase() + "%";
            where.and("(LOWER(equipment_name) LIKE ? OR LOWER(category) LIKE ?"
                    + " OR LOWER(serial_number) LIKE ? OR LOWER(location) LIKE ?)",
                    like, like, like, like);
        }

        where.andNotBlank(criteria.filter("category"), "category = ?");
        where.andNotBlank(criteria.filter("location"), "location = ?");
        where.andNotBlank(criteria.filter("condition"), "`condition` = ?");
        where.andNotBlank(criteria.filter("status"), "status = ?");

        return where;
    }

    // ==================================================================
    //  Extra look-ups
    // ==================================================================

    public boolean existsBySerialNumber(String serialNumber, Long excludeId) {
        SqlCriteria where = new SqlCriteria().and("serial_number = ?", serialNumber);
        if (excludeId != null) {
            where.and("id <> ?", excludeId);
        }
        return exists("SELECT COUNT(*) FROM equipment " + where.getWhere(),
                where.getParamsArray());
    }

    public Optional<Equipment> findBySerialNumber(String serialNumber) {
        return queryOne(BASE_SELECT + " WHERE serial_number = ?", EQUIPMENT_ROW, serialNumber);
    }

    /** Change only the physical condition of an item (quick action in the UI). */
    public boolean updateCondition(Long id, EquipmentCondition condition) {
        return update("UPDATE equipment SET `condition` = ? WHERE id = ?", condition, id) > 0;
    }

    /** Adjust only the stock quantity. */
    public boolean updateQuantity(Long id, int quantity) {
        return update("UPDATE equipment SET quantity = ? WHERE id = ?", quantity, id) > 0;
    }

    public List<String> findDistinctCategories() {
        return query("SELECT DISTINCT category FROM equipment ORDER BY category",
                (rs, rowNum) -> str(rs, "category"));
    }

    public List<String> findDistinctLocations() {
        return query("SELECT DISTINCT location FROM equipment ORDER BY location",
                (rs, rowNum) -> str(rs, "location"));
    }
}
