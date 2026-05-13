package io.github.matrixlane.metadataregistry.schemaextraction.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents a database index.
 */
@Data
@Builder
public class IndexInfo {

    private String indexName;
    private Boolean unique;
    private String type;
    private List<String> columnNames;
    private String filterCondition;
}
