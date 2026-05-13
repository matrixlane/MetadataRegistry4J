package io.github.matrixlane.metadataregistry.schemaextraction.model;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a foreign key relationship.
 */
@Data
@Builder
public class ForeignKeyInfo {

    private String constraintName;
    private String columnName;
    private String referencedTableName;
    private String referencedColumnName;
    private Integer updateRule;
    private Integer deleteRule;
}
