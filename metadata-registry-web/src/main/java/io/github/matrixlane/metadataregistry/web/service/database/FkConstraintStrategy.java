package io.github.matrixlane.metadataregistry.web.service.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Dialect-specific handling of foreign-key enforcement during bulk deletes.
 * Implementations must be idempotent and leave the connection usable for the
 * remainder of the surrounding transaction.
 */
public interface FkConstraintStrategy {

    DatabaseVendor vendor();

    /** Invoked before any DELETE statement is issued. */
    default void beforeBulkDelete(Connection conn, List<TableRef> tables) throws SQLException {
        // default: nothing to do
    }

    /** Invoked after all DELETE statements succeeded. */
    default void afterBulkDelete(Connection conn, List<TableRef> tables) throws SQLException {
        // default: nothing to do
    }
}
