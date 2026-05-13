package io.github.matrixlane.metadataregistry.schemaextraction.postgresql;

import io.github.matrixlane.metadataregistry.schemaextraction.model.ColumnInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.model.DatabaseSchema;
import io.github.matrixlane.metadataregistry.schemaextraction.model.TableInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.DatabaseType;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.sql.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostgreSqlSchemaExtractorTest {

    private final PostgreSqlSchemaExtractor extractor = new PostgreSqlSchemaExtractor();

    @Test
    void shouldSupportPostgreSql() {
        assertThat(extractor.supports(DatabaseType.POSTGRESQL)).isTrue();
        assertThat(extractor.supports(DatabaseType.SQL_SERVER)).isFalse();
        assertThat(extractor.supports(DatabaseType.MYSQL)).isFalse();
    }

    @Test
    void shouldExtractSchemaWithComments() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        PreparedStatement tableStmt = mock(PreparedStatement.class);
        PreparedStatement colStmt = mock(PreparedStatement.class);
        ResultSet tableRs = mock(ResultSet.class);
        ResultSet colRs = mock(ResultSet.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("testdb");
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metaData.getDatabaseProductVersion()).thenReturn("15.4");

        // Tables
        ResultSet tablesRs = createResultSet(List.of(
                mapOf("TABLE_NAME", "patients", "TABLE_TYPE", "TABLE", "REMARKS", null)
        ));
        when(metaData.getTables("testdb", null, "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tablesRs);

        // Columns
        ResultSet columnsRs = createResultSet(List.of(
                mapOf("COLUMN_NAME", "patient_id", "ORDINAL_POSITION", 1, "TYPE_NAME", "varchar",
                        "COLUMN_SIZE", 50, "DECIMAL_DIGITS", null, "NULLABLE", 0,
                        "COLUMN_DEF", null, "REMARKS", null,
                        "IS_AUTOINCREMENT", "NO", "IS_GENERATEDCOLUMN", "NO"),
                mapOf("COLUMN_NAME", "name", "ORDINAL_POSITION", 2, "TYPE_NAME", "varchar",
                        "COLUMN_SIZE", 100, "DECIMAL_DIGITS", null, "NULLABLE", 1,
                        "COLUMN_DEF", null, "REMARKS", null,
                        "IS_AUTOINCREMENT", "NO", "IS_GENERATEDCOLUMN", "NO")
        ));
        when(metaData.getColumns("testdb", null, "patients", "%")).thenReturn(columnsRs);

        // Primary key
        ResultSet pkRs = createResultSet(List.of(
                mapOf("COLUMN_NAME", "patient_id", "PK_NAME", "patients_pkey")
        ));
        when(metaData.getPrimaryKeys("testdb", null, "patients")).thenReturn(pkRs);

        // Indexes
        ResultSet idxRs = createResultSet(Collections.emptyList());
        when(metaData.getIndexInfo("testdb", null, "patients", false, true)).thenReturn(idxRs);

        // Foreign keys
        ResultSet fkRs = createResultSet(Collections.emptyList());
        when(metaData.getImportedKeys("testdb", null, "patients")).thenReturn(fkRs);

        // Table comment via pg_description
        when(connection.prepareStatement(contains("pg_class"))).thenReturn(tableStmt);
        when(tableStmt.executeQuery()).thenReturn(tableRs);
        when(tableRs.next()).thenReturn(true, false);
        when(tableRs.getString("description")).thenReturn("Patient demographic information");

        // Column comments via pg_catalog
        when(connection.prepareStatement(contains("pg_catalog.pg_attribute"))).thenReturn(colStmt);
        when(colStmt.executeQuery()).thenReturn(colRs);
        when(colRs.next()).thenReturn(true, true, false);
        when(colRs.getString("column_name")).thenReturn("patient_id", "name");
        when(colRs.getString("description")).thenReturn("Unique patient identifier", "Patient full name");

        DatabaseSchema schema = extractor.extractSchema(connection, "testdb", null);

        assertThat(schema.getTables()).hasSize(1);
        TableInfo table = schema.getTables().get(0);
        assertThat(table.getTableName()).isEqualTo("patients");
        assertThat(table.getRemarks()).isEqualTo("Patient demographic information");

        assertThat(table.getColumns()).hasSize(2);
        assertThat(table.getColumns().get(0).getRemarks()).isEqualTo("Unique patient identifier");
        assertThat(table.getColumns().get(1).getRemarks()).isEqualTo("Patient full name");
    }

    @Test
    void shouldGracefullyHandleMissingComments() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("testdb");
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metaData.getDatabaseProductVersion()).thenReturn("15.4");

        ResultSet tablesRs = createResultSet(List.of(
                mapOf("TABLE_NAME", "simple", "TABLE_TYPE", "TABLE", "REMARKS", "original remark")
        ));
        when(metaData.getTables("testdb", null, "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tablesRs);

        ResultSet columnsRs = createResultSet(List.of(
                mapOf("COLUMN_NAME", "id", "ORDINAL_POSITION", 1, "TYPE_NAME", "int4",
                        "COLUMN_SIZE", 10, "DECIMAL_DIGITS", 0, "NULLABLE", 0,
                        "COLUMN_DEF", null, "REMARKS", "original col remark",
                        "IS_AUTOINCREMENT", "NO", "IS_GENERATEDCOLUMN", "NO")
        ));
        when(metaData.getColumns("testdb", null, "simple", "%")).thenReturn(columnsRs);

        ResultSet pkRs = createResultSet(Collections.emptyList());
        when(metaData.getPrimaryKeys("testdb", null, "simple")).thenReturn(pkRs);

        ResultSet idxRs = createResultSet(Collections.emptyList());
        when(metaData.getIndexInfo("testdb", null, "simple", false, true)).thenReturn(idxRs);

        ResultSet fkRs = createResultSet(Collections.emptyList());
        when(metaData.getImportedKeys("testdb", null, "simple")).thenReturn(fkRs);

        // Simulate SQL exception when querying pg_description
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("Permission denied"));

        DatabaseSchema schema = extractor.extractSchema(connection, "testdb", null);

        TableInfo table = schema.getTables().get(0);
        assertThat(table.getRemarks()).isEqualTo("original remark");
        assertThat(table.getColumns().get(0).getRemarks()).isEqualTo("original col remark");
    }

    @SuppressWarnings("unchecked")
    private ResultSet createResultSet(List<Map<String, Object>> rows) throws SQLException {
        ResultSet rs = mock(ResultSet.class);

        if (rows.isEmpty()) {
            when(rs.next()).thenReturn(false);
            return rs;
        }

        final int[] currentIndex = {-1};
        when(rs.next()).thenAnswer((InvocationOnMock invocation) -> {
            currentIndex[0]++;
            return currentIndex[0] < rows.size();
        });

        when(rs.getString(anyString())).thenAnswer((InvocationOnMock invocation) -> {
            String column = invocation.getArgument(0);
            Map<String, Object> row = rows.get(currentIndex[0]);
            Object value = row.get(column);
            return value != null ? value.toString() : null;
        });

        final String[] lastIntColumn = {null};
        when(rs.getInt(anyString())).thenAnswer((InvocationOnMock invocation) -> {
            String column = invocation.getArgument(0);
            lastIntColumn[0] = column;
            Map<String, Object> row = rows.get(currentIndex[0]);
            Object value = row.get(column);
            return value != null ? ((Number) value).intValue() : 0;
        });
        when(rs.wasNull()).thenAnswer((InvocationOnMock invocation) -> {
            if (lastIntColumn[0] == null) return false;
            Map<String, Object> row = rows.get(currentIndex[0]);
            return row.get(lastIntColumn[0]) == null;
        });

        when(rs.getBoolean(anyString())).thenAnswer((InvocationOnMock invocation) -> {
            String column = invocation.getArgument(0);
            Map<String, Object> row = rows.get(currentIndex[0]);
            Object value = row.get(column);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return false;
        });

        return rs;
    }

    @SafeVarargs
    private static Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
