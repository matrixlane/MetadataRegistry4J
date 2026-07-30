package io.github.matrixlane.metadataregistry.web.dto.context;

import java.util.List;
import java.util.UUID;

public record ContextUsageItem(
        UUID itemId,
        String itemLabel,
        List<ContextUsageAssociation> associations) {
}
