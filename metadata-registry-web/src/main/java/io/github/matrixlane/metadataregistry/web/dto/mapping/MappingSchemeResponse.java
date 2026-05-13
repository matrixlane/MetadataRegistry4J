package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for a mapping scheme.
 *
 * <p>Contains all fields including server-managed version info and
 * lifecycle status.
 */
public record MappingSchemeResponse(

        UUID id,

        String name,

        String sourceUri,

        String targetUri,

        String description,

        String version,

        MappingSchemeStatus status,

        UUID parentId,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {
}
