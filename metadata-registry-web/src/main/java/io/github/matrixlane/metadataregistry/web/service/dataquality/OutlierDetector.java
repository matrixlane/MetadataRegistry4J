package io.github.matrixlane.metadataregistry.web.service.dataquality;

import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.OutlierStat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 异常值检测器.
 *
 * <p>支持两种方法：
 * <ul>
 *   <li><b>IQR</b>（四分位距法）：基于箱线图的经典异常值检测</li>
 *   <li><b>THREE_SIGMA</b>（3σ 原则）：基于正态分布的异常值检测</li>
 * </ul>
 *
 * <p>异常值示例必须脱敏，不返回真实数据值。
 * 仅返回行号或值范围概略。
 */
public class OutlierDetector {

    private final double defaultThresholdPercent;

    public OutlierDetector(double defaultThresholdPercent) {
        this.defaultThresholdPercent = defaultThresholdPercent;
    }

    /**
     * 检测所有字段的异常值.
     *
     * @param columnData 字段名 -> 字段值列表
     * @param methodPerColumn 字段名 -> 检测方法（IQR 或 THREE_SIGMA），null 表示 IQR
     * @param thresholdPerColumn 字段名 -> 自定义阈值，null 表示使用默认阈值
     * @return 每个数值字段的异常值检测结果
     */
    public List<OutlierStat> detect(Map<String, List<Object>> columnData,
                                     Map<String, String> methodPerColumn,
                                     Map<String, Double> thresholdPerColumn) {
        List<OutlierStat> results = new ArrayList<>();

        for (Map.Entry<String, List<Object>> entry : columnData.entrySet()) {
            String columnName = entry.getKey();
            List<Object> values = entry.getValue();
            String method = methodPerColumn != null && methodPerColumn.containsKey(columnName)
                    ? methodPerColumn.get(columnName)
                    : "IQR";
            double threshold = thresholdPerColumn != null && thresholdPerColumn.containsKey(columnName)
                    ? thresholdPerColumn.get(columnName)
                    : defaultThresholdPercent;

            // Check if numeric
            List<Double> numericValues = extractNumeric(values);
            if (numericValues.isEmpty()) {
                continue; // Skip non-numeric columns
            }

            results.add(detectColumn(columnName, numericValues, values.size(), method, threshold));
        }

        return results;
    }

    /**
     * 检测单个字段的异常值.
     */
    public OutlierStat detectColumn(String columnName, List<Double> numericValues,
                                     int totalCount, String method, double thresholdPercent) {
        OutlierStat stat = new OutlierStat();
        stat.setColumnName(columnName);
        stat.setMethod(method);
        stat.setTotalCount(totalCount);
        stat.setThresholdPercent(thresholdPercent);

        List<Double> sorted = new ArrayList<>(numericValues);
        Collections.sort(sorted);

        double lowerBound;
        double upperBound;

        if ("THREE_SIGMA".equalsIgnoreCase(method)) {
            // 3σ method
            double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = sorted.stream()
                    .mapToDouble(v -> Math.pow(v - mean, 2))
                    .average().orElse(0.0);
            double stdDev = Math.sqrt(variance);

            lowerBound = mean - 3 * stdDev;
            upperBound = mean + 3 * stdDev;

            stat.setSigmaLowerBound(Math.round(lowerBound * 100.0) / 100.0);
            stat.setSigmaUpperBound(Math.round(upperBound * 100.0) / 100.0);
        } else {
            // IQR method (default)
            int n = sorted.size();
            double q1 = percentile(sorted, 25);
            double q3 = percentile(sorted, 75);
            double iqr = q3 - q1;
            double multiplier = 1.5;

            lowerBound = q1 - multiplier * iqr;
            upperBound = q3 + multiplier * iqr;

            stat.setIqrLowerBound(Math.round(lowerBound * 100.0) / 100.0);
            stat.setIqrUpperBound(Math.round(upperBound * 100.0) / 100.0);
        }

        // Count outliers
        List<Double> outliers = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            double v = sorted.get(i);
            if (v < lowerBound || v > upperBound) {
                outliers.add(v);
            }
        }

        stat.setOutlierCount(outliers.size());
        stat.setOutlierPercent(totalCount > 0
                ? Math.round((double) outliers.size() / totalCount * 10000.0) / 100.0
                : 0.0);
        stat.setAboveThreshold(stat.getOutlierPercent() > thresholdPercent);

        // Build de-identified examples (row numbers, up to 10)
        List<String> examples = buildDeidentifiedExamples(numericValues, lowerBound, upperBound, 10);
        stat.setOutlierExamples(examples);

        return stat;
    }

    /**
     * 计算百分位数（线性插值）.
     */
    double percentile(List<Double> sorted, double percentile) {
        if (sorted == null || sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.get(0);

        int n = sorted.size();
        double rank = percentile / 100.0 * (n - 1);
        int lowerIdx = (int) Math.floor(rank);
        int upperIdx = (int) Math.ceil(rank);

        if (lowerIdx == upperIdx) {
            return sorted.get(lowerIdx);
        }

        double weight = rank - lowerIdx;
        return sorted.get(lowerIdx) * (1 - weight) + sorted.get(upperIdx) * weight;
    }

    /**
     * 提取数值列表（脱敏用，排除 null 和非数值）.
     */
    List<Double> extractNumeric(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return values.stream()
                .map(this::toDouble)
                .filter(v -> v != null && !v.isNaN() && !v.isInfinite())
                .toList();
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /**
     * 构建脱敏的异常值示例.
     *
     * <p>按照约束要求，不返回真实数据值。
     * 返回格式："row_<index>"（行号）。
     * 最多返回 maxExamples 条。
     */
    List<String> buildDeidentifiedExamples(List<Double> allNumericValues,
                                            double lowerBound, double upperBound, int maxExamples) {
        List<String> examples = new ArrayList<>();

        // Find indices of outlier values (the actual row number in the original data)
        int count = 0;
        for (int i = 0; i < allNumericValues.size() && count < maxExamples; i++) {
            double v = allNumericValues.get(i);
            if (v < lowerBound || v > upperBound) {
                examples.add("row_" + i);
                count++;
            }
        }

        return examples;
    }
}
