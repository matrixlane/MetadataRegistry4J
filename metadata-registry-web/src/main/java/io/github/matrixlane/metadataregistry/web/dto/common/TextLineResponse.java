package io.github.matrixlane.metadataregistry.web.dto.common;

import java.util.List;
import java.util.UUID;

public record TextLineResponse(
        UUID id,
        UUID languageId,
        String languageCode,
        String value,
        UUID sourceId,
        String sourceTitle,
        List<TextLineContextResponse> contexts,
        /** 指称关联的命名规范 id（Definition 行恒为 null）。回退恢复依此还原基线关联。 */
        List<UUID> namingConventionIds) {
}
