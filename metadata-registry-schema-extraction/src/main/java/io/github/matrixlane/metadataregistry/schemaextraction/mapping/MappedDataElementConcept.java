package io.github.matrixlane.metadataregistry.schemaextraction.mapping;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a mapped Data Element Concept derived from a database table.
 */
@Data
@Builder
public class MappedDataElementConcept {

    private String name;
    private String description;
    private String tableName;
    private String objectClassName;
    private String propertyName;
}
