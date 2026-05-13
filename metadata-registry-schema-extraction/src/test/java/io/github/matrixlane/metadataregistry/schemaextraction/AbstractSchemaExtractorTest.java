package io.github.matrixlane.metadataregistry.schemaextraction;

import io.github.matrixlane.metadataregistry.schemaextraction.model.*;
import io.github.matrixlane.metadataregistry.schemaextraction.sqlserver.SqlServerSchemaExtractor;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.sql.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AbstractSchemaExtractorTest {

    private final SqlServerSchemaExtractor extractor = new SqlServerSchemaExtractor();

    @Test
    void shouldExtractEmptySchema() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("testdb");
        when(metaData.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(metaData.getDatabaseProductVersion()).thenReturn("16.0");

        ResultSet emptyTables = createResultSet(Collections.emptyList());
        when(metaData.getTables("testdb", null, "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(emptyTables);

        DatabaseSchema schema = extractor.extractSchema(connection, "testdb", null);

        assertThat(schema).isNotNull();
        assertThat(schema.getDatabaseProductName()).isEqualTo("Microsoft SQL Server");
        assertThat(schema.getTables()).isEmpty();
    }

    @Test
    void shouldExtractTableWithColumns() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet epRs = createResultSet(Collections.emptyList());

        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("testdb");
        when(metaData.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(metaData.getDatabaseProductVersion()).thenReturn("16.0");

        // Tables
        ResultSet tablesRs = createResultSet(List.of(
                mapOf("TABLE_NAME", "users", "TABLE_TYPE", "TABLE", "REMARKS", "user table")
        ));
        when(metaData.getTables("testdb", null, "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tablesRs);

        // Columns
        ResultSet columnsRs = createResultSet(List.of(
                mapOf("COLUMN_NAME", "id", "ORDINAL_POSITION", 1, "TYPE_NAME", "bigint",
                        "COLUMN_SIZE", 19, "DECIMAL_DIGITS", 0, "NULLABLE", 0,
                        "COLUMN_DEF", null, "REMARKS", null,
                        "IS_AUTOINCREMENT", "YES", "IS_GENERATEDCOLUMN", "NO"),
                mapOf("COLUMN_NAME", "name", "ORDINAL_POSITION", 2, "TYPE_NAME", "nvarchar",
                        "COLUMN_SIZE", 255, "DECIMAL_DIGITS", null, "NULLABLE", 1,
                        "COLUMN_DEF", null, "REMARKS", null,
                        "IS_AUTOINCREMENT", "NO", "IS_GENERATEDCOLUMN", "NO")
        ));
        when(metaData.getColumns("testdb", null, "users", "%")).thenReturn(columnsRs);

        // Primary key
        ResultSet pkRs = createResultSet(List.of(
                mapOf("COLUMN_NAME", "id", "PK_NAME", "PK_users")
        ));
        when(metaData.getPrimaryKeys("testdb", null, "users")).thenReturn(pkRs);

        // Indexes
        ResultSet idxRs = createResultSet(Collections.emptyList());
        when(metaData.getIndexInfo("testdb", null, "users", false, true)).thenReturn(idxRs);

        // Foreign keys
        ResultSet fkRs = createResultSet(Collections.emptyList());
        when(metaData.getImportedKeys("testdb", null, "users")).thenReturn(fkRs);

        // Extended properties - return empty
        when(connection.prepareStatement(contains("sys.extended_properties"))).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(epRs);

        DatabaseSchema schema = extractor.extractSchema(connection, "testdb", null);

        assertThat(schema.getTables()).hasSize(1);
        TableInfo table = schema.getTables().get(0);
        assertThat(table.getTableName()).isEqualTo("users");
        assertThat(table.getTableType()).isEqualTo("TABLE");
        assertThat(table.getColumns()).hasSize(2);
        assertThat(table.getPrimaryKey()).isNotNull();
        assertThat(table.getPrimaryKey().getColumnNames()).containsExactly("id");

        ColumnInfo idCol = table.getColumns().get(0);
        assertThat(idCol.getColumnName()).isEqualTo("id");
        assertThat(idCol.getAutoIncrement()).isTrue();
        assertThat(idCol.getNullable()).isFalse();

        ColumnInfo nameCol = table.getColumns().get(1);
        assertThat(nameCol.getColumnName()).isEqualTo("name");
        assertThat(nameCol.getNullable()).isTrue();
        assertThat(nameCol.getColumnSize()).isEqualTo(255);
    }

    @Test
    void shouldExtractIndexes() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet epRs = createResultSet(Collections.emptyList());

        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("testdb");
        when(metaData.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(metaData.getDatabaseProductVersion()).thenReturn("16.0");

        ResultSet tablesRs = createResultSet(List.of(
                mapOf("TABLE_NAME", "orders", "TABLE_TYPE", "TABLE", "REMARKS", "")
        ));
        when(metaData.getTables("testdb", null, "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tablesRs);

        ResultSet columnsRs = createResultSet(Collections.emptyList());
        when(metaData.getColumns("testdb", null, "orders", "%")).thenReturn(columnsRs);

        ResultSet pkRs = createResultSet(Collections.emptyList());
        when(metaData.getPrimaryKeys("testdb", null, "orders")).thenReturn(pkRs);

        ResultSet idxRs = createResultSet(List.of(
                mapOf("INDEX_NAME", "IX_customer", "NON_UNIQUE", true, "TYPE", 3,
                        "COLUMN_NAME", "customer_id", "FILTER_CONDITION", null),
                mapOf("INDEX_NAME", "UQ_order_no", "NON_UNIQUE", false, "TYPE", 3,
                        "COLUMN_NAME", "order_no", "FILTER_CONDITION", null)
        ));
        when(metaData.getIndexInfo("testdb", null, "orders", false, true)).thenReturn(idxRs);

        ResultSet fkRs = createResultSet(Collections.emptyList());
        when(metaData.getImportedKeys("testdb", null, "orders")).thenReturn(fkRs);

        when(connection.prepareStatement(contains("sys.extended_properties"))).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(epRs);

        DatabaseSchema schema = extractor.extractSchema(connection, "testdb", null);

        TableInfo table = schema.getTables().get(0);
        assertThat(table.getIndexes()).hasSize(2);
        assertThat(table.getIndexes().get(0).getIndexName()).isEqualTo("IX_customer");
        assertThat(table.getIndexes().get(0).getUnique()).isFalse();
        assertThat(table.getIndexes().get(1).getIndexName()).isEqualTo("UQ_order_no");
        assertThat(table.getIndexes().get(1).getUnique()).isTrue();
    }

    @Test
    void shouldExtractForeignKeys() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet epRs = createResultSet(Collections.emptyList());

        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.getCatalog()).thenReturn("testdb");
        when(metaData.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(metaData.getDatabaseProductVersion()).thenReturn("16.0");

        ResultSet tablesRs = createResultSet(List.of(
                mapOf("TABLE_NAME", "orders", "TABLE_TYPE", "TABLE", "REMARKS", "")
        ));
        when(metaData.getTables("testdb", null, "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tablesRs);

        ResultSet columnsRs = createResultSet(Collections.emptyList());
        when(metaData.getColumns("testdb", null, "orders", "%")).thenReturn(columnsRs);

        ResultSet pkRs = createResultSet(Collections.emptyList());
        when(metaData.getPrimaryKeys("testdb", null, "orders")).thenReturn(pkRs);

        ResultSet idxRs = createResultSet(Collections.emptyList());
        when(metaData.getIndexInfo("testdb", null, "orders", false, true)).thenReturn(idxRs);

        ResultSet fkRs = createResultSet(List.of(
                mapOf("FK_NAME", "FK_orders_customer", "FKCOLUMN_NAME", "customer_id",
                        "PKTABLE_NAME", "customers", "PKCOLUMN_NAME", "id",
                        "UPDATE_RULE", 1, "DELETE_RULE", 0)
        ));
        when(metaData.getImportedKeys("testdb", null, "orders")).thenReturn(fkRs);

        when(connection.prepareStatement(contains("sys.extended_properties"))).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(epRs);

        DatabaseSchema schema = extractor.extractSchema(connection, "testdb", null);

        TableInfo table = schema.getTables().get(0);
        assertThat(table.getForeignKeys()).hasSize(1);
        ForeignKeyInfo fk = table.getForeignKeys().get(0);
        assertThat(fk.getColumnName()).isEqualTo("customer_id");
        assertThat(fk.getReferencedTableName()).isEqualTo("customers");
        assertThat(fk.getReferencedColumnName()).isEqualTo("id");
        assertThat(fk.getUpdateRule()).isEqualTo(1);
        assertThat(fk.getDeleteRule()).isEqualTo(0);
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

        // Mock getString
        when(rs.getString(anyString())).thenAnswer((InvocationOnMock invocation) -> {
            String column = invocation.getArgument(0);
            Map<String, Object> row = rows.get(currentIndex[0]);
            Object value = row.get(column);
            return value != null ? value.toString() : null;
        });

        // Mock getInt with wasNull tracking
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

        // Mock getBoolean
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
