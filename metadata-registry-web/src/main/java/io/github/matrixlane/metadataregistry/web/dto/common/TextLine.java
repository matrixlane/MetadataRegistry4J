package io.github.matrixlane.metadataregistry.web.dto.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
        List<UUID> namingConventionIds) {

    public TextLine(UUID languageId, String value, UUID sourceId, List<TextLineContextEntry> contexts) {
        this(languageId, value, sourceId, contexts, null);
    }
}
