package io.github.matrixlane.metadataregistry.web.dto.designationdefinition;

import java.util.UUID;
import java.util.List;

public record DefinitionResponse(
       UUID id,
       UUID itemId,
       UUID languageId,
       String languageIdentifier,
       String value,
       UUID sourceId,
       String sourceTitle,
        List<ContextResponse> contexts) {

    public record ContextResponse(
            UUID contextId,
            String contextLabel,
            String acceptability) {
    }
}
