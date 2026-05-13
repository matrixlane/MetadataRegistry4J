package io.github.matrixlane.metadataregistry.schemaextraction.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents a primary key constraint.
 */
@Data
@Builder
public class PrimaryKeyInfo {

    private String constraintName;
    private List<String> columnNames;
}
