package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactDirection;
import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactLevel;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for mapping impact analysis.
 */
public record ImpactAnalysisResponse(
        UUID changedItemId,
        String changeType,
        List<ImpactedItemDto> impactedItems,
        int totalImpacted,
        boolean truncated
) {

    public record ImpactedItemDto(
            UUID itemId,
            ImpactLevel impactLevel,
            ImpactDirection impactDirection,
            UUID mappingId,
            MappingDegree degree,
            String suggestedAction
    ) {}
}
