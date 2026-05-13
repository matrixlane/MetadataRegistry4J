package io.github.matrixlane.metadataregistry.schemaextraction.openapi.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a schema (data model) extracted from an OpenAPI document.
 * Maps to a DataElementConcept in the metadata registry.
 */
@Data
@Builder(toBuilder = true)
public class OpenApiSchemaInfo {

    private String name;
    private String description;
    private String title;
    private String type;
    private String referencePath;

    @Builder.Default
    private List<OpenApiPropertyInfo> properties = new ArrayList<>();

    @Builder.Default
    private List<String> requiredFields = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    private String suggestedObjectClassName;
    private int propertyCount;
    private int extractedDataElementCount;
}
