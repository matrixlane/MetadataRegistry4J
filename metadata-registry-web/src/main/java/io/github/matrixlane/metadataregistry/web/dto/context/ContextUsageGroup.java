package io.github.matrixlane.metadataregistry.web.dto.context;

import java.util.List;

public record ContextUsageGroup(
        String itemType,
        String itemTypeLabel,
        List<ContextUsageItem> items) {
}
