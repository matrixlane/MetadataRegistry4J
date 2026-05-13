package io.github.matrixlane.metadataregistry.web.dto.version;

import java.time.LocalDateTime;

public record VersionHistoryEntryResponse(
        Long id,
        Integer versionNumber,
        String changeType,
        String changedBy,
        LocalDateTime changedAt,
        String changeSummary
) {
}
