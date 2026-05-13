package io.github.matrixlane.metadataregistry.schemaextraction.openapi.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a property/field extracted from an OpenAPI Schema.
 */
@Data
@Builder(toBuilder = true)
public class OpenApiPropertyInfo {

    private String name;
    private String description;
    private String openApiType;
    private String openApiFormat;
    private String suggestedDataType;
    private boolean required;
    private boolean array;
    private String itemType;
    private String reference;
    private List<String> enumValues;
    private String pattern;
    private Integer minLength;
    private Integer maxLength;
    private Number minimum;
    private Number maximum;
    private String example;
    private String defaultValue;
    private boolean nestedObject;
    private String compositeType;
    private List<String> compositeReferences;

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private List<String> mappedPermissibleValues = new ArrayList<>();
}
