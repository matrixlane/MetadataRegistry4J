package io.github.matrixlane.metadataregistry.schemaextraction.mapping;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of mapping a database schema to administered item candidates.
 */
@Data
@Builder
public class SchemaMappingResult {

    private String databaseProductName;
    private String catalog;
    private String schema;

    @Builder.Default
    private List<MappedDataElementConcept> dataElementConcepts = new ArrayList<>();

    @Builder.Default
    private List<MappedValueDomain> valueDomains = new ArrayList<>();

    @Builder.Default
    private List<MappedDataElement> dataElements = new ArrayList<>();

    @Builder.Default
    private List<MappedRelationship> relationships = new ArrayList<>();
}
