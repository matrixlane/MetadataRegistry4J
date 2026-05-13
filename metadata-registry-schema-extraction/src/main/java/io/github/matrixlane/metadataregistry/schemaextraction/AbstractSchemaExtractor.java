package io.github.matrixlane.metadataregistry.schemaextraction;

import io.github.matrixlane.metadataregistry.schemaextraction.exception.SchemaExtractionException;
import io.github.matrixlane.metadataregistry.schemaextraction.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Abstract base class providing common JDBC schema extraction logic.
 */
public abstract class AbstractSchemaExtractor implements io.github.matrixlane.metadataregistry.schemaextraction.strategy.SchemaExtractionStrategy {

    private static final Logger log = LoggerFactory.getLogger(AbstractSchemaExtractor.class);

    protected static final String[] TABLE_TYPES = {"TABLE", "VIEW"};

    @Override
    public DatabaseSchema extractSchema(Connection connection, String catalog, String schema) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String actualCatalog = catalog != null ? catalog : connection.getCatalog();
            String actualSchema = schema;

            DatabaseSchema.DatabaseSchemaBuilder schemaBuilder = DatabaseSchema.builder()
                    .databaseProductName(metaData.getDatabaseProductName())
                    .databaseProductVersion(metaData.getDatabaseProductVersion())
                    .catalog(actualCatalog)
                    .schema(actualSchema);

            List<TableInfo> tables = extractTables(connection, metaData, actualCatalog, actualSchema);
            schemaBuilder.tables(tables);

            return schemaBuilder.build();
        } catch (SQLException e) {
            throw new SchemaExtractionException("Failed to extract schema", e);
        }
    }

    protected List<TableInfo> extractTables(Connection connection, DatabaseMetaData metaData,
                                            String catalog, String schema) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();

        try (ResultSet rs = metaData.getTables(catalog, schema, "%", TABLE_TYPES)) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String tableType = rs.getString("TABLE_TYPE");
                String remarks = rs.getString("REMARKS");

                TableInfo.TableInfoBuilder tableBuilder = TableInfo.builder()
                        .tableName(tableName)
                        .tableType(tableType)
                        .remarks(remarks);

                tableBuilder.columns(extractColumns(metaData, catalog, schema, tableName));
                tableBuilder.primaryKey(extractPrimaryKey(metaData, catalog, schema, tableName));
                tableBuilder.indexes(extractIndexes(metaData, catalog, schema, tableName));
                tableBuilder.foreignKeys(extractForeignKeys(metaData, catalog, schema, tableName));

                // Allow subclasses to enrich table info (e.g., comments from system tables)
                enrichTableInfo(connection, tableBuilder, catalog, schema, tableName);

                tables.add(tableBuilder.build());
            }
        }

        log.info("Extracted {} tables from catalog='{}' schema='{}'", tables.size(), catalog, schema);
        return tables;
    }

    protected List<ColumnInfo> extractColumns(DatabaseMetaData metaData, String catalog,
                                              String schema, String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();

        try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                ColumnInfo column = ColumnInfo.builder()
                        .columnName(rs.getString("COLUMN_NAME"))
                        .ordinalPosition(rs.getInt("ORDINAL_POSITION"))
                        .dataType(rs.getString("TYPE_NAME"))
                        .columnSize(getNullableInt(rs, "COLUMN_SIZE"))
                        .decimalDigits(getNullableInt(rs, "DECIMAL_DIGITS"))
                        .nullable(mapNullable(rs.getInt("NULLABLE")))
                        .defaultValue(rs.getString("COLUMN_DEF"))
                        .remarks(rs.getString("REMARKS"))
                        .autoIncrement(mapYesNo(rs.getString("IS_AUTOINCREMENT")))
                        .generatedColumn(mapYesNo(rs.getString("IS_GENERATEDCOLUMN")))
                        .build();
                columns.add(column);
            }
        }

        return columns;
    }

    protected PrimaryKeyInfo extractPrimaryKey(DatabaseMetaData metaData, String catalog,
                                               String schema, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        String constraintName = null;

        try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                if (constraintName == null) {
                    constraintName = rs.getString("PK_NAME");
                }
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }

        if (columns.isEmpty()) {
            return null;
        }

        return PrimaryKeyInfo.builder()
                .constraintName(constraintName)
                .columnNames(columns)
                .build();
    }

    protected List<IndexInfo> extractIndexes(DatabaseMetaData metaData, String catalog,
                                             String schema, String tableName) throws SQLException {
        Map<String, IndexInfo.IndexInfoBuilder> indexBuilders = new LinkedHashMap<>();
        Map<String, List<String>> indexColumns = new LinkedHashMap<>();

        try (ResultSet rs = metaData.getIndexInfo(catalog, schema, tableName, false, true)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null) {
                    continue; // Skip table statistics
                }

                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                int indexType = rs.getInt("TYPE");

                IndexInfo.IndexInfoBuilder builder = indexBuilders.computeIfAbsent(indexName, k ->
                        IndexInfo.builder()
                                .indexName(indexName)
                                .unique(!nonUnique)
                                .type(mapIndexType(indexType))
                );

                String columnName = rs.getString("COLUMN_NAME");
                if (columnName != null) {
                    indexColumns.computeIfAbsent(indexName, k -> new ArrayList<>()).add(columnName);
                }

                String filter = rs.getString("FILTER_CONDITION");
                if (filter != null) {
                    builder.filterCondition(filter);
                }
            }
        }

        List<IndexInfo> indexes = new ArrayList<>();
        for (Map.Entry<String, IndexInfo.IndexInfoBuilder> entry : indexBuilders.entrySet()) {
            List<String> columns = indexColumns.get(entry.getKey());
            if (columns != null) {
                entry.getValue().columnNames(columns);
            }
            indexes.add(entry.getValue().build());
        }
        return indexes;
    }

    protected List<ForeignKeyInfo> extractForeignKeys(DatabaseMetaData metaData, String catalog,
                                                      String schema, String tableName) throws SQLException {
        List<ForeignKeyInfo> foreignKeys = new ArrayList<>();

        try (ResultSet rs = metaData.getImportedKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                ForeignKeyInfo fk = ForeignKeyInfo.builder()
                        .constraintName(rs.getString("FK_NAME"))
                        .columnName(rs.getString("FKCOLUMN_NAME"))
                        .referencedTableName(rs.getString("PKTABLE_NAME"))
                        .referencedColumnName(rs.getString("PKCOLUMN_NAME"))
                        .updateRule(rs.getInt("UPDATE_RULE"))
                        .deleteRule(rs.getInt("DELETE_RULE"))
                        .build();
                foreignKeys.add(fk);
            }
        }

        return foreignKeys;
    }

    /**
     * Subclasses can override to enrich table info from system tables.
     */
    protected void enrichTableInfo(Connection connection, TableInfo.TableInfoBuilder tableBuilder,
                                   String catalog, String schema, String tableName) {
        // Default: no enrichment
    }

    protected Integer getNullableInt(ResultSet rs, String columnLabel) throws SQLException {
        int value = rs.getInt(columnLabel);
        if (rs.wasNull()) {
            return null;
        }
        return value;
    }

    protected Boolean mapNullable(int nullable) {
        return nullable == DatabaseMetaData.columnNullable ? Boolean.TRUE :
                (nullable == DatabaseMetaData.columnNoNulls ? Boolean.FALSE : null);
    }

    protected Boolean mapYesNo(String value) {
        if (value == null) {
            return null;
        }
        return "YES".equalsIgnoreCase(value);
    }

    protected String mapIndexType(int type) {
        return switch (type) {
            case DatabaseMetaData.tableIndexClustered -> "CLUSTERED";
            case DatabaseMetaData.tableIndexHashed -> "HASHED";
            case DatabaseMetaData.tableIndexOther -> "OTHER";
            default -> "UNKNOWN";
        };
    }
}
