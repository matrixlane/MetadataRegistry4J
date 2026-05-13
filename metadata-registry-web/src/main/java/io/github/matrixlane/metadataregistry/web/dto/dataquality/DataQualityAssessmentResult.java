package io.github.matrixlane.metadataregistry.web.dto.dataquality;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据质量初评结果.
 *
 * <p>包含空值率统计、数据分布分析、异常值检测等完整评估信息。
 * 遵循"轻量级初评"定位，不扩展为数据质量平台。
 */
public class DataQualityAssessmentResult {

    /** 评估的源表信息. */
    private TableInfo tableInfo;

    /** 采样信息（全量统计时 methods=full）. */
    private SamplingInfo samplingInfo;

    /** 各字段的空值率统计. */
    private List<NullValueStat> nullValueStats;

    /** 各字段的数据分布分析. */
    private List<DistributionStat> distributionStats;

    /** 各数值字段的异常值检测. */
    private List<OutlierStat> outlierStats;

    /** 概览. */
    private Summary summary;

    /** 评估时间. */
    private LocalDateTime assessedAt;

    /** 空表标记. */
    private boolean emptyTable;

    public DataQualityAssessmentResult() {}

    // ── Getters & Setters ───────────────────────────────────────

    public TableInfo getTableInfo() { return tableInfo; }
    public void setTableInfo(TableInfo tableInfo) { this.tableInfo = tableInfo; }

    public SamplingInfo getSamplingInfo() { return samplingInfo; }
    public void setSamplingInfo(SamplingInfo samplingInfo) { this.samplingInfo = samplingInfo; }

    public List<NullValueStat> getNullValueStats() { return nullValueStats; }
    public void setNullValueStats(List<NullValueStat> nullValueStats) { this.nullValueStats = nullValueStats; }

    public List<DistributionStat> getDistributionStats() { return distributionStats; }
    public void setDistributionStats(List<DistributionStat> distributionStats) { this.distributionStats = distributionStats; }

    public List<OutlierStat> getOutlierStats() { return outlierStats; }
    public void setOutlierStats(List<OutlierStat> outlierStats) { this.outlierStats = outlierStats; }

    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }

    public LocalDateTime getAssessedAt() { return assessedAt; }
    public void setAssessedAt(LocalDateTime assessedAt) { this.assessedAt = assessedAt; }

    public boolean isEmptyTable() { return emptyTable; }
    public void setEmptyTable(boolean emptyTable) { this.emptyTable = emptyTable; }

    // ─────────────────────────────────────────────────────────────
    // Nested types
    // ─────────────────────────────────────────────────────────────

    /**
     * 表基本信息.
     */
    public static class TableInfo {
        private String schema;
        private String tableName;
        private long estimatedRowCount;

        public TableInfo() {}

        public TableInfo(String schema, String tableName, long estimatedRowCount) {
            this.schema = schema;
            this.tableName = tableName;
            this.estimatedRowCount = estimatedRowCount;
        }

        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public long getEstimatedRowCount() { return estimatedRowCount; }
        public void setEstimatedRowCount(long estimatedRowCount) { this.estimatedRowCount = estimatedRowCount; }
    }

    /**
     * 采样信息.
     */
    public static class SamplingInfo {
        /** 采样方法：full, random, tablesample, top. */
        private String method;

        /** 实际采样行数. */
        private long sampledRows;

        /** 表中总行数. */
        private long totalRows;

        /** 采样比例（0-1）. */
        private double samplingRate;

        /** 置信度说明（如：95% 置信度下误差 ±2%）. */
        private String confidenceNote;

        public SamplingInfo() {}

        public SamplingInfo(String method, long sampledRows, long totalRows, double samplingRate, String confidenceNote) {
            this.method = method;
            this.sampledRows = sampledRows;
            this.totalRows = totalRows;
            this.samplingRate = samplingRate;
            this.confidenceNote = confidenceNote;
        }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public long getSampledRows() { return sampledRows; }
        public void setSampledRows(long sampledRows) { this.sampledRows = sampledRows; }

        public long getTotalRows() { return totalRows; }
        public void setTotalRows(long totalRows) { this.totalRows = totalRows; }

        public double getSamplingRate() { return samplingRate; }
        public void setSamplingRate(double samplingRate) { this.samplingRate = samplingRate; }

        public String getConfidenceNote() { return confidenceNote; }
        public void setConfidenceNote(String confidenceNote) { this.confidenceNote = confidenceNote; }
    }

    /**
     * 空值率统计.
     */
    public static class NullValueStat {
        private String columnName;
        private long nullCount;
        private long totalCount;
        private double nullPercent;
        private boolean aboveThreshold;
        private double thresholdPercent;

        public NullValueStat() {}

        public NullValueStat(String columnName, long nullCount, long totalCount,
                             double nullPercent, boolean aboveThreshold, double thresholdPercent) {
            this.columnName = columnName;
            this.nullCount = nullCount;
            this.totalCount = totalCount;
            this.nullPercent = nullPercent;
            this.aboveThreshold = aboveThreshold;
            this.thresholdPercent = thresholdPercent;
        }

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }

        public long getNullCount() { return nullCount; }
        public void setNullCount(long nullCount) { this.nullCount = nullCount; }

        public long getTotalCount() { return totalCount; }
        public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

        public double getNullPercent() { return nullPercent; }
        public void setNullPercent(double nullPercent) { this.nullPercent = nullPercent; }

        public boolean isAboveThreshold() { return aboveThreshold; }
        public void setAboveThreshold(boolean aboveThreshold) { this.aboveThreshold = aboveThreshold; }

        public double getThresholdPercent() { return thresholdPercent; }
        public void setThresholdPercent(double thresholdPercent) { this.thresholdPercent = thresholdPercent; }
    }

    /**
     * 数据分布分析.
     */
    public static class DistributionStat {
        private String columnName;

        /** 字段基数（唯一值数量）. */
        private long cardinality;

        /** 是否空表/全空字段（此时统计值为 N/A）. */
        private boolean notApplicable;

        /** 枚举值分布（Top 10，值为 null 时表示非枚举类型或不可用）. */
        private List<ValueFrequency> topValues;

        // 数值字段统计
        private Double numericMin;
        private Double numericMax;
        private Double numericMean;
        private Double numericMedian;
        private Double numericStdDev;

        // 日期字段统计
        private String dateEarliest;
        private String dateLatest;
        private String dateSpanDays;

        // 字符串字段统计
        private Integer minLength;
        private Integer maxLength;

        public DistributionStat() {}

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }

        public long getCardinality() { return cardinality; }
        public void setCardinality(long cardinality) { this.cardinality = cardinality; }

        public boolean isNotApplicable() { return notApplicable; }
        public void setNotApplicable(boolean notApplicable) { this.notApplicable = notApplicable; }

        public List<ValueFrequency> getTopValues() { return topValues; }
        public void setTopValues(List<ValueFrequency> topValues) { this.topValues = topValues; }

        public Double getNumericMin() { return numericMin; }
        public void setNumericMin(Double numericMin) { this.numericMin = numericMin; }

        public Double getNumericMax() { return numericMax; }
        public void setNumericMax(Double numericMax) { this.numericMax = numericMax; }

        public Double getNumericMean() { return numericMean; }
        public void setNumericMean(Double numericMean) { this.numericMean = numericMean; }

        public Double getNumericMedian() { return numericMedian; }
        public void setNumericMedian(Double numericMedian) { this.numericMedian = numericMedian; }

        public Double getNumericStdDev() { return numericStdDev; }
        public void setNumericStdDev(Double numericStdDev) { this.numericStdDev = numericStdDev; }

        public String getDateEarliest() { return dateEarliest; }
        public void setDateEarliest(String dateEarliest) { this.dateEarliest = dateEarliest; }

        public String getDateLatest() { return dateLatest; }
        public void setDateLatest(String dateLatest) { this.dateLatest = dateLatest; }

        public String getDateSpanDays() { return dateSpanDays; }
        public void setDateSpanDays(String dateSpanDays) { this.dateSpanDays = dateSpanDays; }

        public Integer getMinLength() { return minLength; }
        public void setMinLength(Integer minLength) { this.minLength = minLength; }

        public Integer getMaxLength() { return maxLength; }
        public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }
    }

    /**
     * 值频率条目.
     */
    public static class ValueFrequency {
        private String value;
        private long count;
        private double percent;

        public ValueFrequency() {}

        public ValueFrequency(String value, long count, double percent) {
            this.value = value;
            this.count = count;
            this.percent = percent;
        }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }

        public double getPercent() { return percent; }
        public void setPercent(double percent) { this.percent = percent; }
    }

    /**
     * 异常值检测结果.
     */
    public static class OutlierStat {
        private String columnName;
        private String method;

        /** 异常值数量. */
        private long outlierCount;
        /** 总行数. */
        private long totalCount;
        /** 异常值占比（0-100）. */
        private double outlierPercent;
        /** 是否超过阈值. */
        private boolean aboveThreshold;
        /** 阈值百分比. */
        private double thresholdPercent;

        /** IQR 上下界（脱敏）. */
        private Double iqrLowerBound;
        private Double iqrUpperBound;

        /** 3σ 上下界（脱敏）. */
        private Double sigmaLowerBound;
        private Double sigmaUpperBound;

        /** 异常值示例：脱敏后的行号列表（最多 10 个）. */
        private List<String> outlierExamples;

        public OutlierStat() {}

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public long getOutlierCount() { return outlierCount; }
        public void setOutlierCount(long outlierCount) { this.outlierCount = outlierCount; }

        public long getTotalCount() { return totalCount; }
        public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

        public double getOutlierPercent() { return outlierPercent; }
        public void setOutlierPercent(double outlierPercent) { this.outlierPercent = outlierPercent; }

        public boolean isAboveThreshold() { return aboveThreshold; }
        public void setAboveThreshold(boolean aboveThreshold) { this.aboveThreshold = aboveThreshold; }

        public double getThresholdPercent() { return thresholdPercent; }
        public void setThresholdPercent(double thresholdPercent) { this.thresholdPercent = thresholdPercent; }

        public Double getIqrLowerBound() { return iqrLowerBound; }
        public void setIqrLowerBound(Double iqrLowerBound) { this.iqrLowerBound = iqrLowerBound; }

        public Double getIqrUpperBound() { return iqrUpperBound; }
        public void setIqrUpperBound(Double iqrUpperBound) { this.iqrUpperBound = iqrUpperBound; }

        public Double getSigmaLowerBound() { return sigmaLowerBound; }
        public void setSigmaLowerBound(Double sigmaLowerBound) { this.sigmaLowerBound = sigmaLowerBound; }

        public Double getSigmaUpperBound() { return sigmaUpperBound; }
        public void setSigmaUpperBound(Double sigmaUpperBound) { this.sigmaUpperBound = sigmaUpperBound; }

        public List<String> getOutlierExamples() { return outlierExamples; }
        public void setOutlierExamples(List<String> outlierExamples) { this.outlierExamples = outlierExamples; }
    }

    /**
     * 评估概览.
     */
    public static class Summary {
        /** 评估字段数. */
        private int totalColumns;
        /** 空值率超阈值字段数. */
        private int highNullColumns;
        /** 异常值超阈值字段数. */
        private int highOutlierColumns;
        /** 空字段数. */
        private int emptyColumns;

        public Summary() {}

        public Summary(int totalColumns, int highNullColumns, int highOutlierColumns, int emptyColumns) {
            this.totalColumns = totalColumns;
            this.highNullColumns = highNullColumns;
            this.highOutlierColumns = highOutlierColumns;
            this.emptyColumns = emptyColumns;
        }

        public int getTotalColumns() { return totalColumns; }
        public void setTotalColumns(int totalColumns) { this.totalColumns = totalColumns; }

        public int getHighNullColumns() { return highNullColumns; }
        public void setHighNullColumns(int highNullColumns) { this.highNullColumns = highNullColumns; }

        public int getHighOutlierColumns() { return highOutlierColumns; }
        public void setHighOutlierColumns(int highOutlierColumns) { this.highOutlierColumns = highOutlierColumns; }

        public int getEmptyColumns() { return emptyColumns; }
        public void setEmptyColumns(int emptyColumns) { this.emptyColumns = emptyColumns; }
    }
}
