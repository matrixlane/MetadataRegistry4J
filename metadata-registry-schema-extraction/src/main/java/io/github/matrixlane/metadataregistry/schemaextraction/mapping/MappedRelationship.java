package io.github.matrixlane.metadataregistry.schemaextraction.mapping;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a mapped foreign key relationship.
 */
@Data
@Builder
public class MappedRelationship {

    private String sourceTable;
    private String sourceColumn;
    private String targetTable;
    private String targetColumn;
    private String constraintName;
}
