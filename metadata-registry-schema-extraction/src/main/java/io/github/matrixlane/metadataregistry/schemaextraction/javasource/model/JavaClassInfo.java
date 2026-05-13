package io.github.matrixlane.metadataregistry.schemaextraction.javasource.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents information extracted from a Java class or enum, including its JavaDoc comment.
 */
@Getter
@Builder
@ToString
public class JavaClassInfo {

    private final String packageName;
    private final String className;
    private final String qualifiedName;
    private final String comment;
    private final boolean isEnum;
    private final boolean isInterface;
    private final boolean deprecated;
    private final String deprecatedReason;

    @Builder.Default
    private final List<JavaFieldInfo> fields = new ArrayList<>();

    @Builder.Default
    private final List<JavaEnumValueInfo> enumValues = new ArrayList<>();

    @Builder.Default
    private final List<String> seeTags = new ArrayList<>();

    @Builder.Default
    private final List<String> typeParameters = new ArrayList<>();
}
