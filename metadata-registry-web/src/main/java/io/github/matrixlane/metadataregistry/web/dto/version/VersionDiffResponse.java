package io.github.matrixlane.metadataregistry.web.dto.version;

import java.time.LocalDateTime;
import java.util.List;

public record VersionDiffResponse(
        String itemId,
        String itemType,
        Integer baseVersion,
        Integer compareVersion,
        LocalDateTime baseChangedAt,
        LocalDateTime compareChangedAt,
        String baseChangedBy,
        String compareChangedBy,
        List<FieldDiffEntry> fields,
        boolean hasDifferences
) {
}
