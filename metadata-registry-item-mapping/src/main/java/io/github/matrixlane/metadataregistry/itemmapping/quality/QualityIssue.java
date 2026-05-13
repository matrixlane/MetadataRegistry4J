package io.github.matrixlane.metadataregistry.itemmapping.quality;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;

import java.util.UUID;

/**
 * Represents a single quality issue found in a mapping.
 *
 * @param mappingId   the UUID of the mapping with the issue
 * @param severity    the severity level
 * @param issueType   the type of issue (e.g. "SYMMETRY_VIOLATION", "MISSING_DESCRIPTION")
 * @param description human-readable description of the issue
 * @param mappingType the mapping type (may be null)
 */
public record QualityIssue(
        UUID mappingId,
        IssueSeverity severity,
        String issueType,
        String description,
        MappingType mappingType
) {
}
