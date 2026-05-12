package io.github.matrixlane.metadataregistry.web.dto.referencedocument;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record ReferenceDocumentRequest(
        @NotBlank(message = "identifier 不能为空") String identifier,
        UUID typeDescriptionId,
        String title,
        String notation,
        String uri,
        List<UUID> languageIds) {
}
