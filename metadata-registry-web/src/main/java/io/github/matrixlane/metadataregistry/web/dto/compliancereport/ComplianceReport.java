package io.github.matrixlane.metadataregistry.web.dto.compliancereport;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合规报告 —— 顶层数据模型.
 *
 * <p>对应 WI-013-3：合规报告生成与导出。
 * 包含所有数据集的对照表、缺失项、评分摘要。
 */
public record ComplianceReport(
    String title,
    LocalDateTime generatedAt,
    String generatedBy,
    List<DataSetReport> dataSetReports,
    OverallScore overallScore
) {}
