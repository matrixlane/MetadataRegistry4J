package io.github.matrixlane.metadataregistry.schemaextraction.model;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a database column's metadata.
 */
@Data
@Builder(toBuilder = true)
public class ColumnInfo {

    private String columnName;
    private int ordinalPosition;
    private String dataType;
    private Integer columnSize;
    private Integer decimalDigits;
    private Boolean nullable;
    private String defaultValue;
    private String remarks;
    private Boolean autoIncrement;
    private Boolean generatedColumn;
}
