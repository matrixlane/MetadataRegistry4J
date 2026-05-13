package io.github.matrixlane.metadataregistry.schemaextraction.mapping;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a mapped Value Domain derived from a database column type.
 */
@Data
@Builder
public class MappedValueDomain {

    private String name;
    private String description;
    private String dataTypeName;
    private Integer maximumCharacterQuantity;
    private Integer decimalPrecision;
    private Integer decimalScale;
    private String format;
}
