package io.github.matrixlane.metadataregistry.web.service.dataquality;

import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.SamplingInfo;

/**
 * 采样策略决策器.
 *
 * <p>根据表行数和请求参数决定评估使用的采样策略：
 * <ul>
 *   <li><b>full</b>：全量统计（小表或用户强制要求）</li>
 *   <li><b>random</b>：随机采样（使用 NEWID() 或类似方法）</li>
 *   <li><b>tablesample</b>：SQL Server TABLESAMPLE 页级采样（大表，接受页级偏差）</li>
 * </ul>
 *
 * <p>SQL Server 的 TABLESAMPLE 存在页级偏差问题（不能保证均匀采样），
 * 此处通过标注采样方法让用户知晓。对于需要较高精度的小表，推荐全量。
 */
public class SamplingStrategy {

    private final long autoFullScanThreshold;

    public SamplingStrategy(long autoFullScanThreshold) {
        this.autoFullScanThreshold = autoFullScanThreshold;
    }

    /**
     * 评估是否需要采样（用户强制全量或小表自动全量时返回 false）.
     */
    public boolean shouldSample(long totalRows, DataQualityAssessmentRequest request) {
        if (request.isForceFullScan()) {
            return false;
        }
        if (totalRows <= autoFullScanThreshold) {
            return false;
        }
        return true;
    }

    /**
     * 确定实际行数.
     * 全量统计时返回 totalRows；采样时返回 min(sampleSize, totalRows).
     */
    public long effectiveRows(long totalRows, boolean useSampling, DataQualityAssessmentRequest request) {
        if (!useSampling) {
            return totalRows;
        }
        return Math.min(request.getSampleSize(), totalRows);
    }

    /**
     * 确定采样方法.
     */
    public String resolveMethod(long totalRows, boolean useSampling, DataQualityAssessmentRequest request) {
        if (!useSampling) {
            return "full";
        }
        // For SQL Server tables larger than 100k, TABLESAMPLE can be more efficient
        // but has page-level bias. We default to random sampling (WHERE ROW or NEWID())
        // for general use, and TABLESAMPLE for very large tables.
        if (totalRows > 500_000) {
            return "tablesample";
        }
        return "random";
    }

    /**
     * 构建采样信息.
     */
    public SamplingInfo buildSamplingInfo(long totalRows, boolean useSampling,
                                           long sampledRows, DataQualityAssessmentRequest request) {
        String method = resolveMethod(totalRows, useSampling, request);
        double samplingRate = totalRows > 0 ? (double) sampledRows / totalRows : 0.0;
        String confidenceNote = useSampling
                ? buildConfidenceNote(method, sampledRows, totalRows)
                : "全量统计，结果准确";

        return new SamplingInfo(method, sampledRows, totalRows, samplingRate, confidenceNote);
    }

    private String buildConfidenceNote(String method, long sampledRows, long totalRows) {
        if ("full".equals(method)) {
            return "全量统计，结果准确";
        }
        // For random sampling, margin of error at 95% confidence
        // Rough estimate: 1.96 * sqrt(p*(1-p)/n) for worst case p=0.5
        double maxMarginOfError = 1.96 * Math.sqrt(0.25 / sampledRows);
        String marginStr = String.format("%.2f", maxMarginOfError * 100);
        return String.format("采样统计，95%% 置信度下最大误差 ±%s%%（采样方法：%s）", marginStr, method);
    }
}
