package io.github.matrixlane.metadataregistry.web.service.dataquality;

import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.DistributionStat;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.ValueFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据分布分析器.
 *
 * <p>对字段进行基数（唯一值数量）统计、枚举值分布分析、
 * 数值字段统计（min/max/mean/median/std）和日期字段统计。
 */
public class DistributionAnalyzer {

    /**
     * 分析所有字段的数据分布.
     *
     * @param columnData  字段名 -> 字段值列表（值可为 null）
     * @param columnHints 字段名 -> 类型提示（numeric, date, string 等）, null 表示 auto-detect
     * @return 每个字段的分布统计
     */
    public List<DistributionStat> analyze(Map<String, List<Object>> columnData,
                                           Map<String, String> columnHints) {
        List<DistributionStat> results = new ArrayList<>();

        for (Map.Entry<String, List<Object>> entry : columnData.entrySet()) {
            String columnName = entry.getKey();
            List<Object> rawValues = entry.getValue();
            String hint = columnHints != null ? columnHints.get(columnName) : null;

            results.add(analyzeColumn(columnName, rawValues, hint));
        }

        return results;
    }

    /**
     * 分析单个字段的数据分布.
     */
    public DistributionStat analyzeColumn(String columnName, List<Object> rawValues, String typeHint) {
        DistributionStat stat = new DistributionStat();
        stat.setColumnName(columnName);

        if (rawValues == null || rawValues.isEmpty()) {
            stat.setNotApplicable(true);
            return stat;
        }

        // Exclude nulls for distribution analysis
        List<Object> nonNullValues = rawValues.stream()
                .filter(v -> v != null)
                .toList();

        if (nonNullValues.isEmpty()) {
            stat.setNotApplicable(true);
            return stat;
        }

        // Calculate cardinality
        long cardinality = nonNullValues.stream()
                .map(Object::toString)
                .distinct()
                .count();
        stat.setCardinality(cardinality);

        // Top 10 value frequencies
        List<ValueFrequency> topValues = computeTopValues(nonNullValues, 10);
        stat.setTopValues(topValues);

        // Type-specific analysis
        String detectedType = detectType(nonNullValues, typeHint);

        switch (detectedType) {
            case "numeric" -> analyzeNumeric(stat, nonNullValues);
            case "date" -> analyzeDate(stat, nonNullValues);
            default -> analyzeString(stat, nonNullValues);
        }

        return stat;
    }

    // ─────────────────────────────────────────────────────────────────
    // Top value frequency
    // ─────────────────────────────────────────────────────────────────

    List<ValueFrequency> computeTopValues(List<Object> values, int limit) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        long totalCount = values.size();

        // Count frequencies
        Map<String, Long> frequencyMap = values.stream()
                .map(Object::toString)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        // Sort by count descending and take top N
        return frequencyMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new ValueFrequency(
                        entry.getKey(),
                        entry.getValue(),
                        Math.round((double) entry.getValue() / totalCount * 10000.0) / 100.0))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────
    // Type detection
    // ─────────────────────────────────────────────────────────────────

    String detectType(List<Object> values, String hint) {
        if (hint != null) {
            return hint;
        }

        // Take a sample of up to 100 values for type detection
        List<Object> sample = values.size() > 100 ? values.subList(0, 100) : values;

        int numericCount = 0;
        int dateCount = 0;
        int totalChecked = 0;

        for (Object v : sample) {
            if (v == null) continue;
            totalChecked++;
            if (v instanceof Number) {
                numericCount++;
            } else if (v instanceof LocalDate || v instanceof LocalDateTime
                    || v instanceof java.sql.Date || v instanceof java.sql.Timestamp) {
                dateCount++;
            } else if (v instanceof String s) {
                // Try to parse as number
                if (isNumericString(s)) {
                    numericCount++;
                }
                // Try to parse as date
                if (isDateString(s)) {
                    dateCount++;
                }
            }
        }

        if (totalChecked == 0) return "string";

        double numericRatio = (double) numericCount / totalChecked;
        double dateRatio = (double) dateCount / totalChecked;

        if (numericRatio > 0.8) return "numeric";
        if (dateRatio > 0.8) return "date";
        return "string";
    }

    private boolean isNumericString(String s) {
        if (s == null || s.isBlank()) return false;
        try {
            new BigDecimal(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDateString(String s) {
        if (s == null || s.isBlank()) return false;
        // Common date patterns
        try {
            LocalDate.parse(s.trim());
            return true;
        } catch (Exception ignored) {}
        try {
            LocalDateTime.parse(s.trim());
            return true;
        } catch (Exception ignored) {}
        // Try common SQL date formats
        try {
            java.sql.Date.valueOf(s.trim());
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    // ─────────────────────────────────────────────────────────────────
    // Numeric field analysis
    // ─────────────────────────────────────────────────────────────────

    private void analyzeNumeric(DistributionStat stat, List<Object> values) {
        List<Double> numericValues = values.stream()
                .map(this::toDouble)
                .filter(v -> v != null && !v.isNaN() && !v.isInfinite())
                .toList();

        if (numericValues.isEmpty()) {
            stat.setNotApplicable(true);
            return;
        }

        stat.setNumericMin(Collections.min(numericValues));
        stat.setNumericMax(Collections.max(numericValues));

        // Mean
        double sum = numericValues.stream().mapToDouble(Double::doubleValue).sum();
        stat.setNumericMean(Math.round(sum / numericValues.size() * 100.0) / 100.0);

        // Median
        List<Double> sorted = new ArrayList<>(numericValues);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 0) {
            stat.setNumericMedian((sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0);
        } else {
            stat.setNumericMedian(sorted.get(size / 2));
        }

        // Standard deviation (population)
        double mean = stat.getNumericMean();
        double variance = numericValues.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum() / numericValues.size();
        stat.setNumericStdDev(Math.round(Math.sqrt(variance) * 100.0) / 100.0);
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Date field analysis
    // ─────────────────────────────────────────────────────────────────

    private void analyzeDate(DistributionStat stat, List<Object> values) {
        List<LocalDate> dates = values.stream()
                .map(this::toLocalDate)
                .filter(d -> d != null)
                .sorted()
                .toList();

        if (dates.isEmpty()) {
            stat.setNotApplicable(true);
            return;
        }

        stat.setDateEarliest(dates.get(0).toString());
        stat.setDateLatest(dates.get(dates.size() - 1).toString());

        long spanDays = ChronoUnit.DAYS.between(dates.get(0), dates.get(dates.size() - 1));
        stat.setDateSpanDays(String.valueOf(spanDays));
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (value instanceof java.sql.Date sd) return sd.toLocalDate();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (value instanceof String s) {
            try { return LocalDate.parse(s.trim()); } catch (Exception ignored) {}
            try { return LocalDateTime.parse(s.trim()).toLocalDate(); } catch (Exception ignored) {}
            try { return java.sql.Date.valueOf(s.trim()).toLocalDate(); } catch (Exception ignored) {}
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // String field analysis
    // ─────────────────────────────────────────────────────────────────

    private void analyzeString(DistributionStat stat, List<Object> values) {
        List<String> stringValues = values.stream()
                .map(v -> v != null ? v.toString() : null)
                .filter(v -> v != null)
                .toList();

        if (stringValues.isEmpty()) {
            stat.setNotApplicable(true);
            return;
        }

        // SQL-style char should be treated as string, so use length
        stat.setMinLength(stringValues.stream().mapToInt(String::length).min().orElse(0));
        stat.setMaxLength(stringValues.stream().mapToInt(String::length).max().orElse(0));
    }
}
