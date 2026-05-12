package io.github.matrixlane.metadataregistry.web.dto.designationdefinition;

import java.util.UUID;

public record DesignationResponse(
        UUID id,
        UUID itemId,
        UUID languageId,
        String languageIdentifier,
        String value,
        UUID sourceId,
        String sourceTitle,
        UUID namespaceId,
        String namespaceLabel,
        UUID contextId,
        String contextLabel,
        String acceptability) {
}
