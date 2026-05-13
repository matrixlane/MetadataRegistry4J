package io.github.matrixlane.metadataregistry.web.service.dataquality;

import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.NullValueStat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 空值率分析器.
 *
 * <p>按字段统计 NULL 值占比，标记空值率超过阈值的字段。
 */
public class NullValueAnalyzer {

    private final double defaultThresholdPercent;

    public NullValueAnalyzer(double defaultThresholdPercent) {
        this.defaultThresholdPercent = defaultThresholdPercent;
    }

    /**
     * 分析空值率.
     *
     * @param columnData 字段名 -> 字段值列表的映射（值可为 null）
     * @param thresholdPerColumn 字段名 -> 自定义阈值（可选），null 表示使用默认阈值
     * @return 每个字段的空值统计
     */
    public List<NullValueStat> analyze(Map<String, List<Object>> columnData,
                                        Map<String, Double> thresholdPerColumn) {
        List<NullValueStat> results = new ArrayList<>();

        for (Map.Entry<String, List<Object>> entry : columnData.entrySet()) {
            String columnName = entry.getKey();
            List<Object> values = entry.getValue();
            double threshold = thresholdPerColumn != null && thresholdPerColumn.containsKey(columnName)
                    ? thresholdPerColumn.get(columnName)
                    : defaultThresholdPercent;

            results.add(analyzeColumn(columnName, values, threshold));
        }

        return results;
    }

    /**
     * 分析单个字段的空值率.
     */
    public NullValueStat analyzeColumn(String columnName, List<Object> values, double thresholdPercent) {
        if (values == null || values.isEmpty()) {
            return new NullValueStat(columnName, 0, 0, 0.0, false, thresholdPercent);
        }

        long nullCount = values.stream().filter(v -> v == null).count();
        long totalCount = values.size();
        double nullPercent = (double) nullCount / totalCount * 100.0;
        boolean aboveThreshold = nullPercent > thresholdPercent;

        return new NullValueStat(columnName, nullCount, totalCount,
                Math.round(nullPercent * 100.0) / 100.0, aboveThreshold, thresholdPercent);
    }
}
