package io.github.matrixlane.metadataregistry.web.dto.dataquality;

import java.util.List;

/**
 * 数据质量初评请求.
 *
 * <p>用户指定需要评估的表、字段、采样策略等参数。
 * 评估对象是遗留系统中的表，非 MDR 领域模型。
 */
public class DataQualityAssessmentRequest {

    /** 要评估的表名（必填）. */
    private String tableName;

    /** 表所属 schema（可选，默认 dbo）. */
    private String schema = "dbo";

    /** 要评估的字段列表（为空表示评估所有字段）. */
    private List<String> columns;

    /** 数据源配置名称/标识（从 WI-016a 的连接信息中引用）. */
    private String dataSourceKey;

    /** 是否强制全量统计（跳过采样，默认 false）. */
    private boolean forceFullScan;

    /** 采样行数（默认 10000）. */
    private int sampleSize = 10000;

    /** 空值率阈值百分比（默认 50）. */
    private double nullThresholdPercent = 50.0;

    /** 异常值占比阈值百分比（默认 5）. */
    private double outlierThresholdPercent = 5.0;

    /** 异常值检测方法：IQR 或 THREE_SIGMA（默认 IQR）. */
    private String outlierMethod = "IQR";

    public DataQualityAssessmentRequest() {}

    // ── Builder-style factory ───────────────────────────────────

    public static DataQualityAssessmentRequest forTable(String tableName, String dataSourceKey) {
        DataQualityAssessmentRequest req = new DataQualityAssessmentRequest();
        req.tableName = tableName;
        req.dataSourceKey = dataSourceKey;
        return req;
    }

    // ── Getters & Setters ───────────────────────────────────────

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public String getDataSourceKey() { return dataSourceKey; }
    public void setDataSourceKey(String dataSourceKey) { this.dataSourceKey = dataSourceKey; }

    public boolean isForceFullScan() { return forceFullScan; }
    public void setForceFullScan(boolean forceFullScan) { this.forceFullScan = forceFullScan; }

    public int getSampleSize() { return sampleSize; }
    public void setSampleSize(int sampleSize) { this.sampleSize = sampleSize; }

    public double getNullThresholdPercent() { return nullThresholdPercent; }
    public void setNullThresholdPercent(double nullThresholdPercent) { this.nullThresholdPercent = nullThresholdPercent; }

    public double getOutlierThresholdPercent() { return outlierThresholdPercent; }
    public void setOutlierThresholdPercent(double outlierThresholdPercent) { this.outlierThresholdPercent = outlierThresholdPercent; }

    public String getOutlierMethod() { return outlierMethod; }
    public void setOutlierMethod(String outlierMethod) { this.outlierMethod = outlierMethod; }
}
