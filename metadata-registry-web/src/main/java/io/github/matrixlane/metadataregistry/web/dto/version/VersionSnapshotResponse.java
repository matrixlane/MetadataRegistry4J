package io.github.matrixlane.metadataregistry.web.dto.version;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record VersionSnapshotResponse(
        Long id,
        Integer versionNumber,
        String changeType,
        String changedBy,
        LocalDateTime changedAt,
        String changeSummary,
        JsonNode snapshot
) {
}
