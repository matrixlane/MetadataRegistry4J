package io.github.matrixlane.metadataregistry.schemaextraction.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the complete schema of a database.
 */
@Data
@Builder
public class DatabaseSchema {

    private String databaseProductName;
    private String databaseProductVersion;
    private String catalog;
    private String schema;
    @Builder.Default
    private List<TableInfo> tables = new ArrayList<>();
}
