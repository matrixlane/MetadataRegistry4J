package io.github.matrixlane.metadataregistry.web.dto.scopedidentifier;

import io.github.matrixlane.metadataregistry.ext.identification.VersionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record IdentifierVersionHistoryResponse(
        UUID id,
        UUID scopedIdentifierId,
        String version,
        VersionStatus versionStatus,
        String operator,
        LocalDateTime timestamp,
        String changeSummary
) {
}
