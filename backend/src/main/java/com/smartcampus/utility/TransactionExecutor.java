package com.smartcampus.utility;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import com.smartcampus.exception.SmartCampusException;

/**
 * Runs a group of JDBC statements as <b>one atomic database transaction</b>.
 *
 * <p>Used by the booking service so that "check for conflict" and "insert the
 * booking" either both succeed or both fail - a partially written booking can
 * never be left behind.</p>
 *
 * <pre>
 *   connection.setAutoCommit(false);
 *   work.run(connection);     // check + insert
 *   connection.commit();      // everything is saved
 *   // or, when work throws: connection.rollback();
 * </pre>
 *
 * <p><b>OOP concepts:</b> <i>generics</i> ({@code <R>}) and a <i>functional
 * interface</i> ({@link SqlWork}) that is always passed as a lambda.</p>
 */
@Component
public class TransactionExecutor {

    private final DataSource dataSource;

    public TransactionExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Executes {@code work} inside a transaction and returns its result.
     *
     * @param work lambda receiving the shared {@link Connection}
     * @return whatever the lambda produced
     */
    public <R> R doInTransaction(SqlWork<R> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                R result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception ex) {
                rollbackQuietly(connection, ex);

                if (ex instanceof SmartCampusException business) {
                    throw business;
                }
                if (ex instanceof SQLException sqlEx) {
                    throw new SmartCampusException(
                            "Database transaction failed. See the server log for details.", sqlEx);
                }
                throw new SmartCampusException("Operation failed: " + ex.getMessage(), ex);
            } finally {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // connection is about to be closed by try-with-resources anyway
                }
            }
        } catch (SQLException ex) {
            throw new SmartCampusException(
                    "Could not obtain a database connection. See the server log for details.", ex);
        }
    }

    private void rollbackQuietly(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    /** Functional interface accepted by {@link #doInTransaction(SqlWork)}. */
    @FunctionalInterface
    public interface SqlWork<R> {
        R run(Connection connection) throws Exception;
    }
}
