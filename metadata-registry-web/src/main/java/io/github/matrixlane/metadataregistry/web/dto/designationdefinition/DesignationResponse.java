package io.github.matrixlane.metadataregistry.web.dto.designationdefinition;

import java.util.List;
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
        List<ContextResponse> contexts) {

    public record ContextResponse(
            UUID contextId,
            String contextLabel,
            String acceptability) {
    }
}
