package com.smartcampus.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.ResourceNotFoundException;
import com.smartcampus.model.Classroom;
import com.smartcampus.model.enums.ResourceStatus;
import com.smartcampus.model.enums.RoomType;
import com.smartcampus.utility.SqlCriteria;

/**
 * JDBC implementation of every classroom query.
 * Extends {@link JdbcSupport} and implements both generic repository
 * interfaces - a concrete example of <i>interface inheritance</i>.
 */
@Repository
public class ClassroomRepository extends JdbcSupport
        implements GenericRepository<Classroom, Long>, SearchableRepository<Classroom> {

    private static final String BASE_SELECT = """
            SELECT id, room_number, building, floor, capacity, type, facilities,
                   status, created_at
            FROM classrooms """;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "roomNumber", "room_number",
            "building", "building",
            "floor", "floor",
            "capacity", "capacity",
            "type", "type",
            "status", "status",
            "id", "id");

    /** Lambda row mapper. */
    private static final RowMapper<Classroom> CLASSROOM_ROW = (rs, rowNum) -> {
        Classroom classroom = new Classroom(
                rs.getLong("id"),
                str(rs, "room_number"));
        classroom.setBuilding(str(rs, "building"));
        classroom.setFloor(intOf(rs, "floor"));
        classroom.setCapacity(intOf(rs, "capacity"));
        classroom.setType(enumOf(RoomType.class, str(rs, "type"), RoomType.CLASSROOM));
        classroom.setFacilities(str(rs, "facilities"));
        classroom.setStatus(enumOf(ResourceStatus.class, str(rs, "status"), ResourceStatus.ACTIVE));
        classroom.setCreatedAt(dateTime(rs, "created_at"));
        return classroom;
    };

    public ClassroomRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Classroom save(Classroom classroom) {
        String sql = """
                INSERT INTO classrooms (room_number, building, floor, capacity, type, facilities, status)
                VALUES (?,?,?,?,?,?,?)""";

        long id = insert(sql,
                classroom.getRoomNumber(), classroom.getBuilding(), classroom.getFloor(),
                classroom.getCapacity(), classroom.getType(), classroom.getFacilities(),
                classroom.getStatus());

        classroom.setId(id);
        return classroom;
    }

    @Override
    public Optional<Classroom> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE id = ?", CLASSROOM_ROW, id);
    }

    @Override
    public List<Classroom> findAll() {
        return query(BASE_SELECT + " ORDER BY room_number ASC", CLASSROOM_ROW);
    }

    @Override
    public Classroom update(Classroom classroom) {
        String sql = """
                UPDATE classrooms SET
                    room_number=?, building=?, floor=?, capacity=?, type=?, facilities=?, status=?
                WHERE id=?""";

        int affected = update(sql,
                classroom.getRoomNumber(), classroom.getBuilding(), classroom.getFloor(),
                classroom.getCapacity(), classroom.getType(), classroom.getFacilities(),
                classroom.getStatus(), classroom.getId());

        if (affected == 0) {
            throw new ResourceNotFoundException(classroom.getId());
        }
        return classroom;
    }

    @Override
    public boolean deleteById(Long id) {
        return update("DELETE FROM classrooms WHERE id = ?", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong("SELECT COUNT(*) FROM classrooms");
    }

    @Override
    public boolean existsById(Long id) {
        return exists("SELECT COUNT(*) FROM classrooms WHERE id = ?", id);
    }

    @Override
    public List<Classroom> search(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);

        String sql = BASE_SELECT
                + where.getWhere() + " "
                + SqlCriteria.orderBy(criteria.getSort(), SORT_COLUMNS, "room_number") + " "
                + SqlCriteria.direction(criteria.getDirection())
                + " LIMIT ?, ?";

        List<Object> params = new ArrayList<>(where.getParams());
        params.add(criteria.offset());
        params.add(criteria.getSize());

        return query(sql, CLASSROOM_ROW, params.toArray());
    }

    @Override
    public long count(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);
        return queryForLong("SELECT COUNT(*) FROM classrooms " + where.getWhere(),
                where.getParamsArray());
    }

    private SqlCriteria buildFilter(SearchCriteria criteria) {
        SqlCriteria where = new SqlCriteria();

        if (criteria.hasKeyword()) {
            String like = "%" + criteria.getKeyword().toLowerCase() + "%";
            where.and("(LOWER(room_number) LIKE ? OR LOWER(building) LIKE ?"
                    + " OR LOWER(facilities) LIKE ?)", like, like, like);
        }

        where.andNotBlank(criteria.filter("building"), "building = ?");
        where.andNotBlank(criteria.filter("type"), "type = ?");
        where.andNotBlank(criteria.filter("status"), "status = ?");
        where.andNumeric(criteria.filter("minCapacity"), "capacity >= ?");

        return where;
    }

    // ==================================================================
    //  Extra look-ups
    // ==================================================================

    public boolean existsByRoomNumber(String roomNumber, Long excludeId) {
        SqlCriteria where = new SqlCriteria().and("room_number = ?", roomNumber);
        if (excludeId != null) {
            where.and("id <> ?", excludeId);
        }
        return exists("SELECT COUNT(*) FROM classrooms " + where.getWhere(),
                where.getParamsArray());
    }

    public Optional<Classroom> findByRoomNumber(String roomNumber) {
        return queryOne(BASE_SELECT + " WHERE room_number = ?", CLASSROOM_ROW, roomNumber);
    }

    public List<String> findDistinctBuildings() {
        return query("SELECT DISTINCT building FROM classrooms ORDER BY building",
                (rs, rowNum) -> str(rs, "building"));
    }
}
