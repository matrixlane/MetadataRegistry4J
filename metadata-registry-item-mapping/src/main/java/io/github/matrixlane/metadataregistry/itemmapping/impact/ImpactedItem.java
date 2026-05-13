package io.github.matrixlane.metadataregistry.itemmapping.impact;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;

import java.util.UUID;

/**
 * Represents a single item affected by a mapping change.
 *
 * @param itemId          the UUID of the affected administered item
 * @param impactLevel     the level of impact (direct / indirect / cascade)
 * @param impactDirection the direction of impact
 * @param mappingId       the UUID of the mapping relationship causing the impact
 * @param mappingType     the type of the mapping relationship
 * @param suggestedAction a human-readable suggestion for remediation
 */
public record ImpactedItem(
        UUID itemId,
        ImpactLevel impactLevel,
        ImpactDirection impactDirection,
        UUID mappingId,
        MappingType mappingType,
        String suggestedAction
) {
}
