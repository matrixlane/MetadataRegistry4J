package io.github.matrixlane.metadataregistry.web.dto.scopedidentifier;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.VersionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScopedIdentifierResponse(
        UUID id,
        UUID namespaceId,
        String namespaceLabel,
        String identifier,
        String version,
        VersionStatus versionStatus,
        UUID identifiedItemId,
        String identifiedItemType,
        String identifiedItemLabel,
        LocalDateTime createdAt,
        LocalDateTime publishedAt,
        LocalDateTime deprecatedAt,
        LocalDateTime obsoleteAt
) {
}
