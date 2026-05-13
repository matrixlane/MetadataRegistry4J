package io.github.matrixlane.metadataregistry.schemaextraction.mysql;

import io.github.matrixlane.metadataregistry.schemaextraction.AbstractSchemaExtractor;
import io.github.matrixlane.metadataregistry.schemaextraction.model.ColumnInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.model.TableInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * MySQL 5.7+ / 8.0+ schema extraction strategy.
 * Reads comments from information_schema.TABLES and information_schema.COLUMNS.
 */
public class MySqlSchemaExtractor extends AbstractSchemaExtractor {

    private static final Logger log = LoggerFactory.getLogger(MySqlSchemaExtractor.class);

    @Override
    public boolean supports(DatabaseType databaseType) {
        return databaseType == DatabaseType.MYSQL;
    }

    @Override
    protected void enrichTableInfo(Connection connection, TableInfo.TableInfoBuilder tableBuilder,
                                   String catalog, String schema, String tableName) {
        try {
            enrichTableRemarks(connection, tableBuilder, catalog, tableName);
            enrichColumnRemarks(connection, tableBuilder, catalog, tableName);
        } catch (SQLException e) {
            log.warn("Failed to enrich comments for table '{}': {}", tableName, e.getMessage());
        }
    }

    private void enrichTableRemarks(Connection connection, TableInfo.TableInfoBuilder tableBuilder,
                                    String catalog, String tableName) throws SQLException {
        String sql = """
                SELECT t.TABLE_COMMENT
                FROM information_schema.TABLES t
                WHERE t.TABLE_SCHEMA = ?
                  AND t.TABLE_NAME = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, catalog != null ? catalog : connection.getCatalog());
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String comment = rs.getString("TABLE_COMMENT");
                    if (comment != null && !comment.isEmpty()) {
                        tableBuilder.remarks(comment);
                    }
                }
            }
        }
    }

    private void enrichColumnRemarks(Connection connection, TableInfo.TableInfoBuilder tableBuilder,
                                     String catalog, String tableName) throws SQLException {
        String sql = """
                SELECT c.COLUMN_NAME, c.COLUMN_COMMENT
                FROM information_schema.COLUMNS c
                WHERE c.TABLE_SCHEMA = ?
                  AND c.TABLE_NAME = ?
                """;

        Map<String, String> columnComments = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, catalog != null ? catalog : connection.getCatalog());
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String comment = rs.getString("COLUMN_COMMENT");
                    if (comment != null && !comment.isEmpty()) {
                        columnComments.put(rs.getString("COLUMN_NAME"), comment);
                    }
                }
            }
        }

        if (!columnComments.isEmpty()) {
            List<ColumnInfo> enrichedColumns = new ArrayList<>();
            TableInfo tempTable = tableBuilder.build();
            for (ColumnInfo col : tempTable.getColumns()) {
                String comment = columnComments.get(col.getColumnName());
                if (comment != null) {
                    enrichedColumns.add(col.toBuilder().remarks(comment).build());
                } else {
                    enrichedColumns.add(col);
                }
            }
            tableBuilder.columns(enrichedColumns);
        }
    }
}
