package io.github.matrixlane.metadataregistry.web.dto.context;

import java.util.List;

public record ContextUsageResponse(
        List<ContextUsageGroup> groups,
        boolean truncated) {
}
