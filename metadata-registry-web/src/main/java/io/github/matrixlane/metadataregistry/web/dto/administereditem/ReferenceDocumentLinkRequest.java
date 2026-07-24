package io.github.matrixlane.metadataregistry.web.dto.administereditem;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReferenceDocumentLinkRequest(
        @NotNull(message = "referenceDocumentIds 不能为空")
        List<UUID> referenceDocumentIds) {
}
