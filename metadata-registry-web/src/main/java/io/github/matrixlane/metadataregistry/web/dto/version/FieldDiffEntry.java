package io.github.matrixlane.metadataregistry.web.dto.version;

public record FieldDiffEntry(
        String fieldName,
        String fieldLabel,
        DiffType type,
        Object oldValue,
        Object newValue
) {
    public enum DiffType {
        ADDED,
        REMOVED,
        MODIFIED,
        UNCHANGED
    }
}
