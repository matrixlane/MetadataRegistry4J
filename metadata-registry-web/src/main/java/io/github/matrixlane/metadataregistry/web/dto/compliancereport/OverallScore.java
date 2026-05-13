package io.github.matrixlane.metadataregistry.web.dto.compliancereport;

import java.util.List;

/**
 * 总体评分摘要.
 *
 * <p>对应 WI-013-3 需求 3：数据质量评分报告。
 * MVP 阶段不包含评分趋势/历史对比。
 */
public record OverallScore(
    double overallScore,
    List<DataSetScore> dataSetScores,
    List<String> suggestions
) {

    public record DataSetScore(
        String dataSetCode,
        String dataSetName,
        double matchRate,
        double qualityScore
    ) {}
}
