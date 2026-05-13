package io.github.matrixlane.metadataregistry.web.dto.compliancereport;

import java.util.List;

/**
 * 单个数据集的合规报告.
 *
 * <p>包含对照表、缺失项、匹配率和质量评分。
 */
public record DataSetReport(
    String dataSetCode,
    String dataSetName,
    List<MappingEntry> mappings,
    List<MissingEntry> missingEntries,
    double matchRate,
    double qualityScore
) {}
