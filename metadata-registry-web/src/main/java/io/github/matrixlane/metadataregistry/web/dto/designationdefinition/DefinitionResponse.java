package io.github.matrixlane.metadataregistry.web.dto.designationdefinition;

import java.util.UUID;

public record DefinitionResponse(
        UUID id,
        UUID itemId,
        UUID languageId,
        String languageIdentifier,
        String value,
        UUID sourceId,
        String sourceTitle,
        UUID contextId,
        String contextLabel,
        String acceptability) {
}
