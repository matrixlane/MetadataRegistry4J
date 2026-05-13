package io.github.matrixlane.metadataregistry.schemaextraction.mapping;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a mapped Data Element derived from a database column.
 */
@Data
@Builder
public class MappedDataElement {

    private String name;
    private String description;
    private String tableName;
    private String columnName;
    private boolean nullable;
    private boolean primaryKey;
    private boolean autoIncrement;
    private MappedDataElementConcept dataElementConcept;
    private MappedValueDomain valueDomain;
}
