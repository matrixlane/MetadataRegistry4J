package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.quality.IssueSeverity;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for mapping quality report.
 */
public record MappingQualityReportResponse(
        UUID schemeId,
        String schemeName,
        QualityMetricsDto metrics,
        List<UUID> highRiskMappings,
        List<QualityIssueDto> issues,
        List<String> improvementTips,
        String generatedAt
) {

    public record QualityMetricsDto(
            double coverageScore,
            double descriptionRate,
            double transformationRate,
            double consistencyScore,
            double overallScore,
            long totalMappings,
            long mappedElements,
            long targetElements,
            int issuesFound
    ) {}

    public record QualityIssueDto(
            UUID mappingId,
            IssueSeverity severity,
            String issueType,
            String description,
            MappingType mappingType
    ) {}
}
