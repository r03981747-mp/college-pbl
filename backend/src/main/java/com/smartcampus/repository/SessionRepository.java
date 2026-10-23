package com.smartcampus.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import com.smartcampus.model.SessionToken;

/**
 * JDBC implementation of the login-session store.
 *
 * <p>Keeping sessions in MySQL (instead of server memory) means a restarted
 * backend still recognises the tokens that were issued before the restart,
 * and it demonstrates real CRUD against the {@code user_sessions} table.</p>
 */
@Repository
public class SessionRepository extends JdbcSupport {

    private static final String BASE_SELECT = """
            SELECT id, user_id, token, expires_at, created_at FROM user_sessions """;

    private static final RowMapper<SessionToken> SESSION_ROW = (rs, rowNum) -> new SessionToken(
            rs.getLong("id"),
            nullableLong(rs, "user_id"),
            str(rs, "token"),
            dateTime(rs, "expires_at"));

    public SessionRepository(DataSource dataSource) {
        super(dataSource);
    }

    /** Issue a brand new session token for a user. */
    public SessionToken save(Long userId, String token, LocalDateTime expiresAt) {
        long id = insert("INSERT INTO user_sessions (user_id, token, expires_at) VALUES (?,?,?)",
                userId, token, expiresAt);
        return new SessionToken(id, userId, token, expiresAt);
    }

    /** Look up a bearer token; the caller must still check {@code isValid()}. */
    public Optional<SessionToken> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return queryOne(BASE_SELECT + " WHERE token = ?", SESSION_ROW, token);
    }

    public List<SessionToken> findByUserId(Long userId) {
        return query(BASE_SELECT + " WHERE user_id = ? ORDER BY created_at DESC",
                SESSION_ROW, userId);
    }

    /** Logout - removes one token. */
    public boolean deleteByToken(String token) {
        return update("DELETE FROM user_sessions WHERE token = ?", token) > 0;
    }

    /** Logout everywhere / account disabled - removes every token of a user. */
    public int deleteByUserId(Long userId) {
        return update("DELETE FROM user_sessions WHERE user_id = ?", userId);
    }

    /** Housekeeping: removes expired sessions (called at startup). */
    public int deleteExpired() {
        return update("DELETE FROM user_sessions WHERE expires_at < ?", LocalDateTime.now());
    }

    public long count() {
        return queryForLong("SELECT COUNT(*) FROM user_sessions");
    }
}
