package io.github.matrixlane.metadataregistry.schemaextraction.javasource.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents information extracted from a Java field, including its JavaDoc comment.
 */
@Getter
@Builder
@ToString
public class JavaFieldInfo {

    private final String fieldName;
    private final String typeName;
    private final String comment;
    private final List<String> paramTags;
    private final List<String> seeTags;
    private final boolean deprecated;
    private final String deprecatedReason;

    @Builder.Default
    private final List<String> modifiers = new ArrayList<>();
}
