package io.github.matrixlane.metadataregistry.schemaextraction.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a database table's complete metadata.
 */
@Data
@Builder
public class TableInfo {

    private String tableName;
    private String tableType;
    private String remarks;
    @Builder.Default
    private List<ColumnInfo> columns = new ArrayList<>();
    private PrimaryKeyInfo primaryKey;
    @Builder.Default
    private List<IndexInfo> indexes = new ArrayList<>();
    @Builder.Default
    private List<ForeignKeyInfo> foreignKeys = new ArrayList<>();
}
