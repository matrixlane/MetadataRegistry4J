package io.github.matrixlane.metadataregistry.web.service.database;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Dameng (Oracle-style): disables each foreign key constraint by name before
 * the bulk delete and re-enables it afterwards. Constraint names are
 * discovered through standard JDBC metadata, so no data-dictionary SQL is
 * required.
 */
@Component
public class DmFkConstraintStrategy implements FkConstraintStrategy {

    private final DatabaseEnvironment env;

    public DmFkConstraintStrategy(DatabaseEnvironment env) {
        this.env = env;
    }

    @Override
    public DatabaseVendor vendor() {
        return DatabaseVendor.DM;
    }

    @Override
    public void beforeBulkDelete(Connection conn, List<TableRef> tables) throws SQLException {
        toggle(conn, tables, "DISABLE");
    }

    @Override
    public void afterBulkDelete(Connection conn, List<TableRef> tables) throws SQLException {
        toggle(conn, tables, "ENABLE");
    }

    private void toggle(Connection conn, List<TableRef> tables, String action) throws SQLException {
        List<String[]> constraints = findFkConstraints(conn, tables);
        try (Statement st = conn.createStatement()) {
            for (String[] pair : constraints) {
                st.execute("ALTER TABLE " + env.qualify(pair[0], pair[1])
                        + " " + action + " CONSTRAINT \"" + pair[2] + "\"");
            }
        }
    }

    private List<String[]> findFkConstraints(Connection conn, List<TableRef> tables) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        List<String[]> result = new ArrayList<>();
        for (TableRef table : tables) {
            try (ResultSet rs = md.getImportedKeys(conn.getCatalog(), table.schema(), table.name())) {
                while (rs.next()) {
                    result.add(new String[]{table.schema(), table.name(), rs.getString("FK_NAME")});
                }
            }
        }
        return result;
    }
}
