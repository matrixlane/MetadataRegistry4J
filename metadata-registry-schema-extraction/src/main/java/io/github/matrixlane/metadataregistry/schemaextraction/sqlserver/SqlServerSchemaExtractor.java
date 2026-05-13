package io.github.matrixlane.metadataregistry.schemaextraction.sqlserver;

import io.github.matrixlane.metadataregistry.schemaextraction.AbstractSchemaExtractor;
import io.github.matrixlane.metadataregistry.schemaextraction.model.ColumnInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.model.TableInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * SQL Server 2016+ schema extraction strategy.
 * Reads extended properties (MS_Description) from sys.extended_properties.
 */
public class SqlServerSchemaExtractor extends AbstractSchemaExtractor {

    private static final Logger log = LoggerFactory.getLogger(SqlServerSchemaExtractor.class);

    @Override
    public boolean supports(DatabaseType databaseType) {
        return databaseType == DatabaseType.SQL_SERVER;
    }

    @Override
    protected void enrichTableInfo(Connection connection, TableInfo.TableInfoBuilder tableBuilder,
                                   String catalog, String schema, String tableName) {
        try {
            enrichTableRemarks(connection, tableBuilder, schema, tableName);
            enrichColumnRemarks(connection, tableBuilder, schema, tableName);
        } catch (SQLException e) {
            log.warn("Failed to enrich extended properties for table '{}': {}", tableName, e.getMessage());
        }
    }

    private void enrichTableRemarks(Connection connection, TableInfo.TableInfoBuilder tableBuilder,
                                    String schema, String tableName) throws SQLException {
        String sql = """
                SELECT ep.value AS description
                FROM sys.extended_properties ep
                INNER JOIN sys.tables t ON ep.major_id = t.object_id
                INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE ep.name = 'MS_Description'
                  AND ep.minor_id = 0
                  AND s.name = ?
                  AND t.name = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema != null ? schema : "dbo");
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String description = rs.getString("description");
                    tableBuilder.remarks(description);
                }
            }
        }
    }

    private void enrichColumnRemarks(Connection connection, TableInfo.TableInfoBuilder tableBuilder,
                                     String schema, String tableName) throws SQLException {
        String sql = """
                SELECT c.name AS column_name, ep.value AS description
                FROM sys.extended_properties ep
                INNER JOIN sys.columns c ON ep.major_id = c.object_id AND ep.minor_id = c.column_id
                INNER JOIN sys.tables t ON c.object_id = t.object_id
                INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
                WHERE ep.name = 'MS_Description'
                  AND s.name = ?
                  AND t.name = ?
                """;

        Map<String, String> columnDescriptions = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema != null ? schema : "dbo");
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columnDescriptions.put(
                            rs.getString("column_name"),
                            rs.getString("description")
                    );
                }
            }
        }

        if (!columnDescriptions.isEmpty()) {
            List<ColumnInfo> enrichedColumns = new ArrayList<>();
            TableInfo tempTable = tableBuilder.build();
            for (ColumnInfo col : tempTable.getColumns()) {
                String description = columnDescriptions.get(col.getColumnName());
                if (description != null) {
                    enrichedColumns.add(col.toBuilder().remarks(description).build());
                } else {
                    enrichedColumns.add(col);
                }
            }
            tableBuilder.columns(enrichedColumns);
        }
    }
}
