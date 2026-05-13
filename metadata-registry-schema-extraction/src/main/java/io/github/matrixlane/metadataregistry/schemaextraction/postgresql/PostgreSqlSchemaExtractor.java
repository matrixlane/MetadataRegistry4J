package io.github.matrixlane.metadataregistry.schemaextraction.postgresql;

import io.github.matrixlane.metadataregistry.schemaextraction.AbstractSchemaExtractor;
import io.github.matrixlane.metadataregistry.schemaextraction.model.ColumnInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.model.TableInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL 12+ schema extraction strategy.
 * Reads comments from pg_description via system catalog functions.
 */
public class PostgreSqlSchemaExtractor extends AbstractSchemaExtractor {

    private static final Logger log = LoggerFactory.getLogger(PostgreSqlSchemaExtractor.class);

    @Override
    public boolean supports(DatabaseType databaseType) {
        return databaseType == DatabaseType.POSTGRESQL;
    }

    @Override
    protected void enrichTableInfo(Connection connection, TableInfo.TableInfoBuilder tableBuilder,
                                   String catalog, String schema, String tableName) {
        try {
            enrichTableRemarks(connection, tableBuilder, schema, tableName);
            enrichColumnRemarks(connection, tableBuilder, schema, tableName);
        } catch (SQLException e) {
            log.warn("Failed to enrich comments for table '{}': {}", tableName, e.getMessage());
        }
    }

    private void enrichTableRemarks(Connection connection, TableInfo.TableInfoBuilder tableBuilder,
                                    String schema, String tableName) throws SQLException {
        String sql = """
                SELECT obj_description(pgc.oid, 'pg_class') AS description
                FROM pg_class pgc
                JOIN pg_namespace pgn ON pgn.oid = pgc.relnamespace
                WHERE pgc.relname = ?
                  AND pgn.nspname = ?
                  AND pgc.relkind IN ('r', 'v', 'm')
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, schema != null ? schema : "public");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String description = rs.getString("description");
                    if (description != null && !description.isEmpty()) {
                        tableBuilder.remarks(description);
                    }
                }
            }
        }
    }

    private void enrichColumnRemarks(Connection connection, TableInfo.TableInfoBuilder tableBuilder,
                                     String schema, String tableName) throws SQLException {
        String sql = """
                SELECT a.attname AS column_name,
                       pg_catalog.col_description(c.oid, a.attnum) AS description
                FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid
                WHERE c.relname = ?
                  AND n.nspname = ?
                  AND a.attnum > 0
                  AND NOT a.attisdropped
                """;

        Map<String, String> columnDescriptions = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, schema != null ? schema : "public");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String description = rs.getString("description");
                    if (description != null && !description.isEmpty()) {
                        columnDescriptions.put(rs.getString("column_name"), description);
                    }
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
