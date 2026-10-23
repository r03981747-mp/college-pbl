package com.smartcampus.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.dto.SearchCriteria;
import com.smartcampus.exception.UserNotFoundException;
import com.smartcampus.model.UserAccount;
import com.smartcampus.model.enums.AccountStatus;
import com.smartcampus.model.enums.Role;
import com.smartcampus.utility.SqlCriteria;

/**
 * JDBC implementation of the user-account queries.
 *
 * <p>The {@code password} column only ever comes <b>out</b> of the database -
 * it is read by the authentication service for verification and never sent to
 * a controller or to the browser.</p>
 */
@Repository
public class UserRepository extends JdbcSupport
        implements GenericRepository<UserAccount, Long>, SearchableRepository<UserAccount> {

    private static final String BASE_SELECT = """
            SELECT id, username, email, password, full_name, role, status, created_at
            FROM users """;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "username", "username",
            "fullName", "full_name",
            "email", "email",
            "role", "role",
            "status", "status",
            "createdAt", "created_at",
            "id", "id");

    /** Lambda row mapper. */
    private static final RowMapper<UserAccount> USER_ROW = (rs, rowNum) -> {
        UserAccount user = new UserAccount(
                rs.getLong("id"),
                str(rs, "username"),
                str(rs, "full_name"),
                enumOf(Role.class, str(rs, "role"), Role.STUDENT));
        user.setEmail(str(rs, "email"));
        user.setPassword(str(rs, "password"));
        user.setStatus(enumOf(AccountStatus.class, str(rs, "status"), AccountStatus.ACTIVE));
        user.setCreatedAt(dateTime(rs, "created_at"));
        return user;
    };

    public UserRepository(DataSource dataSource) {
        super(dataSource);
    }

    // ==================================================================
    //  GenericRepository
    // ==================================================================

    @Override
    public UserAccount save(UserAccount user) {
        String sql = """
                INSERT INTO users (username, email, password, full_name, role, status)
                VALUES (?,?,?,?,?,?)""";

        long id = insert(sql, user.getUsername(), user.getEmail(), user.getPassword(),
                user.getFullName(), user.getRole(), user.getStatus());

        user.setId(id);
        return user;
    }

    @Override
    public Optional<UserAccount> findById(Long id) {
        return queryOne(BASE_SELECT + " WHERE id = ?", USER_ROW, id);
    }

    @Override
    public List<UserAccount> findAll() {
        return query(BASE_SELECT + " ORDER BY full_name ASC", USER_ROW);
    }

    @Override
    public UserAccount update(UserAccount user) {
        String sql = """
                UPDATE users SET username=?, email=?, full_name=?, role=?, status=?
                WHERE id=?""";

        int affected = update(sql, user.getUsername(), user.getEmail(), user.getFullName(),
                user.getRole(), user.getStatus(), user.getId());

        if (affected == 0) {
            throw new UserNotFoundException(user.getId());
        }
        return user;
    }

    @Override
    public boolean deleteById(Long id) {
        return update("DELETE FROM users WHERE id = ?", id) > 0;
    }

    @Override
    public long count() {
        return queryForLong("SELECT COUNT(*) FROM users");
    }

    @Override
    public boolean existsById(Long id) {
        return exists("SELECT COUNT(*) FROM users WHERE id = ?", id);
    }

    // ==================================================================
    //  SearchableRepository
    // ==================================================================

    @Override
    public List<UserAccount> search(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);

        String sql = BASE_SELECT
                + where.getWhere() + " "
                + SqlCriteria.orderBy(criteria.getSort(), SORT_COLUMNS, "id") + " "
                + SqlCriteria.direction(criteria.getDirection())
                + " LIMIT ?, ?";

        List<Object> params = new ArrayList<>(where.getParams());
        params.add(criteria.offset());
        params.add(criteria.getSize());

        return query(sql, USER_ROW, params.toArray());
    }

    @Override
    public long count(SearchCriteria criteria) {
        SqlCriteria where = buildFilter(criteria);
        return queryForLong("SELECT COUNT(*) FROM users " + where.getWhere(),
                where.getParamsArray());
    }

    private SqlCriteria buildFilter(SearchCriteria criteria) {
        SqlCriteria where = new SqlCriteria();

        if (criteria.hasKeyword()) {
            String like = "%" + criteria.getKeyword().toLowerCase() + "%";
            where.and("(LOWER(username) LIKE ? OR LOWER(full_name) LIKE ?"
                    + " OR LOWER(email) LIKE ?)", like, like, like);
        }

        where.andNotBlank(criteria.filter("role"), "role = ?");
        where.andNotBlank(criteria.filter("status"), "status = ?");

        return where;
    }

    // ==================================================================
    //  Authentication look-ups
    // ==================================================================

    /** Login accepts either the username or the e-mail address. */
    public Optional<UserAccount> findByUsernameOrEmail(String usernameOrEmail) {
        return queryOne(BASE_SELECT + " WHERE username = ? OR email = ?",
                USER_ROW, usernameOrEmail, usernameOrEmail);
    }

    public Optional<UserAccount> findByUsername(String username) {
        return queryOne(BASE_SELECT + " WHERE username = ?", USER_ROW, username);
    }

    public Optional<UserAccount> findByEmail(String email) {
        return queryOne(BASE_SELECT + " WHERE email = ?", USER_ROW, email);
    }

    public boolean existsByUsername(String username, Long excludeId) {
        SqlCriteria where = new SqlCriteria().and("username = ?", username);
        if (excludeId != null) {
            where.and("id <> ?", excludeId);
        }
        return exists("SELECT COUNT(*) FROM users " + where.getWhere(),
                where.getParamsArray());
    }

    public boolean existsByEmail(String email, Long excludeId) {
        SqlCriteria where = new SqlCriteria().and("email = ?", email);
        if (excludeId != null) {
            where.and("id <> ?", excludeId);
        }
        return exists("SELECT COUNT(*) FROM users " + where.getWhere(),
                where.getParamsArray());
    }

    /** Replaces the stored BCrypt hash with a freshly computed one. */
    public boolean updatePassword(Long id, String encodedPassword) {
        return update("UPDATE users SET password = ? WHERE id = ?", encodedPassword, id) > 0;
    }

    public List<String> findDistinctRoles() {
        return query("SELECT DISTINCT role FROM users ORDER BY role",
                (rs, rowNum) -> str(rs, "role"));
    }
}
