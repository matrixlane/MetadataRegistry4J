package io.github.matrixlane.metadataregistry.itemmapping.quality;

import java.util.List;
import java.util.UUID;

/**
 * Quality report for a mapping scheme.
 *
 * @param schemeId          the UUID of the mapping scheme
 * @param schemeName        the name of the mapping scheme
 * @param metrics           the quality metrics
 * @param highRiskMappings  list of high-risk mapping IDs
 * @param issues            list of all quality issues found
 * @param improvementTips   list of improvement suggestions
 * @param generatedAt       ISO-8601 timestamp of report generation
 */
public record MappingQualityReport(
        UUID schemeId,
        String schemeName,
        MappingQualityMetrics metrics,
        List<UUID> highRiskMappings,
        List<QualityIssue> issues,
        List<String> improvementTips,
        String generatedAt
) {
}
