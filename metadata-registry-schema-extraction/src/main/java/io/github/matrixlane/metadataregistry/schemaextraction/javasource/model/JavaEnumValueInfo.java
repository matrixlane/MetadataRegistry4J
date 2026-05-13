package io.github.matrixlane.metadataregistry.schemaextraction.javasource.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents information extracted from an enum constant, including its JavaDoc comment.
 */
@Getter
@Builder
@ToString
public class JavaEnumValueInfo {

    private final String name;
    private final String comment;
    private final boolean deprecated;
    private final String deprecatedReason;
}
