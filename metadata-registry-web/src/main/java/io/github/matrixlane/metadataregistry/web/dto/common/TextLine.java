package io.github.matrixlane.metadataregistry.web.dto.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
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
        Boolean isolatedException,
        Acceptability acceptability) {

    public TextLine(UUID languageId, String value, UUID sourceId, List<TextLineContextEntry> contexts) {
        this(null, languageId, value, sourceId, contexts, null, null, null);
    }

    public TextLine(UUID languageId, String value, UUID sourceId, List<TextLineContextEntry> contexts, List<UUID> namingConventionIds) {
        this(null, languageId, value, sourceId, contexts, namingConventionIds, null, null);
    }

    public TextLine(UUID languageId, String value, UUID sourceId, List<TextLineContextEntry> contexts, List<UUID> namingConventionIds, Boolean isolatedException) {
        this(null, languageId, value, sourceId, contexts, namingConventionIds, isolatedException, null);
    }

    public TextLine(UUID id, UUID languageId, String value, UUID sourceId, List<TextLineContextEntry> contexts,
                    List<UUID> namingConventionIds, Boolean isolatedException, Acceptability acceptability) {
        this.id = id;
        this.languageId = languageId;
        this.value = value;
        this.sourceId = sourceId;
        this.contexts = contexts;
        this.namingConventionIds = namingConventionIds;
        this.isolatedException = isolatedException;
        this.acceptability = acceptability;
    }
}
