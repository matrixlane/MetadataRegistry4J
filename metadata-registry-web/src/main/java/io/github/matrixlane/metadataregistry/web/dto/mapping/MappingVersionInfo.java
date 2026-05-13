package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight info about a mapping scheme version for version list display.
 *
 * <p>Used in the version lineage endpoint to show all available versions
 * of a mapping scheme for diff selection.
 */
public record MappingVersionInfo(
        UUID id,
        String version,
        MappingSchemeStatus status,
        UUID parentId,
        LocalDateTime createdAt
) {
}
