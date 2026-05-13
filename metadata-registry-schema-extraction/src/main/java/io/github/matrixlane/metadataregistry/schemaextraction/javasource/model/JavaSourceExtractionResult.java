package io.github.matrixlane.metadataregistry.schemaextraction.javasource.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated result of extracting comments from Java source files.
 */
@Getter
@Builder
@ToString
public class JavaSourceExtractionResult {

    @Builder.Default
    private final List<JavaClassInfo> classes = new ArrayList<>();

    @Builder.Default
    private final List<String> errors = new ArrayList<>();

    private final int totalFilesProcessed;

    public int getClassCount() {
        return classes.size();
    }

    public int getFieldCount() {
        return classes.stream()
                .mapToInt(c -> c.getFields().size())
                .sum();
    }

    public int getEnumCount() {
        return (int) classes.stream()
                .filter(JavaClassInfo::isEnum)
                .count();
    }

    public int getEnumValueCount() {
        return classes.stream()
                .mapToInt(c -> c.getEnumValues().size())
                .sum();
    }
}
