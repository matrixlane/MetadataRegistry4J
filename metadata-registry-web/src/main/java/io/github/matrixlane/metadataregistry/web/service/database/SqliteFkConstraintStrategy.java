package io.github.matrixlane.metadataregistry.web.service.database;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * SQLite: {@code PRAGMA defer_foreign_keys = ON} defers FK enforcement to the
 * end of the current transaction, which is exactly the bulk-delete use case:
 * every row is gone by commit time, so no violation is ever raised. Unlike
 * {@code PRAGMA foreign_keys = OFF} it works inside an active transaction.
 */
@Component
public class SqliteFkConstraintStrategy implements FkConstraintStrategy {

    @Override
    public DatabaseVendor vendor() {
        return DatabaseVendor.SQLITE;
    }

    @Override
    public void beforeBulkDelete(Connection conn, List<TableRef> tables) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA defer_foreign_keys = ON");
        }
    }
}
