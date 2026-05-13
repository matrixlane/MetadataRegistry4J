package io.github.matrixlane.metadataregistry.itemmapping.quality;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Quality metrics for a mapping scheme.
 *
 * @param coverageScore       percentage of target elements mapped (0-100)
 * @param descriptionRate     percentage of mappings with non-empty description (0-100)
 * @param transformationRate  percentage of relevant mappings with transformation rules (0-100)
 * @param consistencyScore    100 minus percentage of contradictory mappings (0-100)
 * @param overallScore        weighted average of all metrics (0-100)
 * @param totalMappings       total number of mappings in the scheme
 * @param mappedElements      number of distinct elements mapped
 * @param targetElements      total number of target standard elements
 * @param issuesFound         total number of quality issues detected
 */
public record MappingQualityMetrics(
        double coverageScore,
        double descriptionRate,
        double transformationRate,
        double consistencyScore,
        double overallScore,
        long totalMappings,
        long mappedElements,
        long targetElements,
        int issuesFound
) {

    private static final int PRECISION = 2;

    public MappingQualityMetrics {
        coverageScore = round(coverageScore);
        descriptionRate = round(descriptionRate);
        transformationRate = round(transformationRate);
        consistencyScore = round(consistencyScore);
        overallScore = round(overallScore);
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(PRECISION, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
