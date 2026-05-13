package io.github.matrixlane.metadataregistry.itemmapping.impact;

import java.util.List;
import java.util.UUID;

/**
 * Result of a mapping impact analysis.
 *
 * @param changedItemId   the UUID of the item that triggered the analysis
 * @param changeType      description of the change (e.g. "UPDATED", "DELETED")
 * @param impactedItems   list of all items impacted by the change
 * @param totalImpacted   total count of unique impacted items
 * @param truncated       whether the analysis was truncated (e.g. exceeded max nodes)
 */
public record ImpactAnalysisResult(
        UUID changedItemId,
        String changeType,
        List<ImpactedItem> impactedItems,
        int totalImpacted,
        boolean truncated
) {
}
