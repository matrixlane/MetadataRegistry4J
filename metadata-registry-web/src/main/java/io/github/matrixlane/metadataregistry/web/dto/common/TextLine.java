package io.github.matrixlane.metadataregistry.web.dto.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record TextLine(
        /** 已持久化行的 id；携带时按 id 就地更新（保留行 id，避免重建触发配对外键冲突），
         *  缺省时视为新建行 */
        UUID id,
        UUID languageId,
        @NotBlank(message = "value 不能为空")
        String value,
        UUID sourceId,
        @Valid
        List<TextLineContextEntry> contexts,
        List<UUID> namingConventionIds,
        Boolean isolatedException) {

    public TextLine(UUID languageId, String value, UUID sourceId, List<TextLineContextEntry> contexts) {
        this(null, languageId, value, sourceId, contexts, null, null);
    }

    public TextLine(UUID languageId, String value, UUID sourceId, List<TextLineContextEntry> contexts, List<UUID> namingConventionIds) {
        this(null, languageId, value, sourceId, contexts, namingConventionIds, null);
    }

    public TextLine(UUID languageId, String value, UUID sourceId, List<TextLineContextEntry> contexts,
                    List<UUID> namingConventionIds, Boolean isolatedException) {
        this(null, languageId, value, sourceId, contexts, namingConventionIds, isolatedException);
    }
}
