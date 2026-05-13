package io.github.matrixlane.metadataregistry.schemaextraction.mapping;

import io.github.matrixlane.metadataregistry.dataspecification.*;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of mapping a database schema to ISO/IEC 11179 administered item entities.
 */
@Data
@Builder
public class AdministeredItemMappingResult {

    private String databaseProductName;
    private String catalog;
    private String schema;

    @Builder.Default
    private List<ObjectClass> objectClasses = new ArrayList<>();

    @Builder.Default
    private List<Property> properties = new ArrayList<>();

    @Builder.Default
    private List<DataElementConcept> dataElementConcepts = new ArrayList<>();

    @Builder.Default
    private List<DataType> dataTypes = new ArrayList<>();

    @Builder.Default
    private List<DescribedValueDomain> valueDomains = new ArrayList<>();

    @Builder.Default
    private List<DataElement> dataElements = new ArrayList<>();
}
