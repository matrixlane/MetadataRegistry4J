package io.github.matrixlane.metadataregistry.web.dto.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import java.util.List;
import java.util.UUID;

public record TextLine(
        @NotNull(message = "language 不能为空")
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
        this(languageId, value, sourceId, contexts, null, null, null);
    }

    public TextLine(UUID languageId, String value, UUID sourceId, List<TextLineContextEntry> contexts, List<UUID> namingConventionIds) {
        this(languageId, value, sourceId, contexts, namingConventionIds, null, null);
    }

    public TextLine(UUID languageId, String value, UUID sourceId, List<TextLineContextEntry> contexts, List<UUID> namingConventionIds, Boolean isolatedException) {
        this(languageId, value, sourceId, contexts, namingConventionIds, isolatedException, null);
    }
}
