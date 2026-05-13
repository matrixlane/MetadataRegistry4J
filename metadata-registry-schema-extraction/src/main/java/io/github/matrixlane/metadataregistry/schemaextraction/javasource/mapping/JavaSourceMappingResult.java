package io.github.matrixlane.metadataregistry.schemaextraction.javasource.mapping;

import io.github.matrixlane.metadataregistry.schemaextraction.mapping.MappedDataElement;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.MappedDataElementConcept;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.MappedValueDomain;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated mapping result from Java source comment extraction to Administered Items.
 */
@Getter
@Builder
@ToString
public class JavaSourceMappingResult {

    @Builder.Default
    private final List<MappedDataElementConcept> dataElementConcepts = new ArrayList<>();

    @Builder.Default
    private final List<MappedDataElement> dataElements = new ArrayList<>();

    @Builder.Default
    private final List<MappedValueDomain> valueDomains = new ArrayList<>();

    @Builder.Default
    private final List<MappedPermissibleValue> permissibleValues = new ArrayList<>();

    @Builder.Default
    private final List<String> warnings = new ArrayList<>();
}
