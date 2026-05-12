package io.github.matrixlane.metadataregistry.web.dto.referencedocument;

import io.github.matrixlane.metadataregistry.web.dto.language.LanguageResponse;

import java.util.List;
import java.util.UUID;

public record ReferenceDocumentResponse(
        UUID id,
        String identifier,
        UUID typeDescriptionId,
        String typeDescriptionLabel,
        String title,
        String notation,
        String uri,
        List<LanguageResponse> languages) {
}
