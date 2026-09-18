package io.github.matrixlane.metadataregistry.web.service.database;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * SQL Server: toggles constraint checking per table with NOCHECK /
 * WITH CHECK CHECK, the same statements the legacy implementation used.
 */
@Component
public class SqlServerFkConstraintStrategy implements FkConstraintStrategy {

    private final DatabaseEnvironment env;

    public SqlServerFkConstraintStrategy(DatabaseEnvironment env) {
        this.env = env;
    }

    @Override
    public DatabaseVendor vendor() {
        return DatabaseVendor.SQLSERVER;
    }

    @Override
    public void beforeBulkDelete(Connection conn, List<TableRef> tables) throws SQLException {
        executeOnEach(conn, tables, " NOCHECK CONSTRAINT ALL");
    }

    @Override
    public void afterBulkDelete(Connection conn, List<TableRef> tables) throws SQLException {
        executeOnEach(conn, tables, " WITH CHECK CHECK CONSTRAINT ALL");
    }

    private void executeOnEach(Connection conn, List<TableRef> tables, String suffix) throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (TableRef table : tables) {
                st.execute("ALTER TABLE " + env.qualify(table.schema(), table.name()) + suffix);
            }
        }
    }
}
