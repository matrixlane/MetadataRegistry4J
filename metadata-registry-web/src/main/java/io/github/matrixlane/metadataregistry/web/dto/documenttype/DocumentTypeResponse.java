package io.github.matrixlane.metadataregistry.web.dto.documenttype;

import java.util.UUID;

public record DocumentTypeResponse(
        UUID id,
        String identifier,
        String description,
        String schemeReference) {
}
