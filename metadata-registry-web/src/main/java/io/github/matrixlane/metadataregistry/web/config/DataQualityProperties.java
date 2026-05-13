package io.github.matrixlane.metadataregistry.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据质量初评配置属性.
 */
@Component
@ConfigurationProperties(prefix = "app.data-quality")
public class DataQualityProperties {

    /** 默认采样行数. */
    private int defaultSampleSize = 10000;

    /** 自动全量统计的行数阈值（小于此值自动全量）. */
    private long autoFullScanThreshold = 10000;

    /** 空值率默认阈值百分比. */
    private double defaultNullThresholdPercent = 50.0;

    /** 异常值占比默认阈值百分比. */
    private double defaultOutlierThresholdPercent = 5.0;

    /** 异常值检测默认方法：IQR 或 THREE_SIGMA. */
    private String defaultOutlierMethod = "IQR";

    /** 最大并发评估任务数. */
    private int maxConcurrentTasks = 2;

    /** 任务结果 TTL 天数. */
    private int resultTtlDays = 7;

    // ── Getters & Setters ───────────────────────────────────────

    public int getDefaultSampleSize() { return defaultSampleSize; }
    public void setDefaultSampleSize(int defaultSampleSize) { this.defaultSampleSize = defaultSampleSize; }

    public long getAutoFullScanThreshold() { return autoFullScanThreshold; }
    public void setAutoFullScanThreshold(long autoFullScanThreshold) { this.autoFullScanThreshold = autoFullScanThreshold; }

    public double getDefaultNullThresholdPercent() { return defaultNullThresholdPercent; }
    public void setDefaultNullThresholdPercent(double defaultNullThresholdPercent) { this.defaultNullThresholdPercent = defaultNullThresholdPercent; }

    public double getDefaultOutlierThresholdPercent() { return defaultOutlierThresholdPercent; }
    public void setDefaultOutlierThresholdPercent(double defaultOutlierThresholdPercent) { this.defaultOutlierThresholdPercent = defaultOutlierThresholdPercent; }

    public String getDefaultOutlierMethod() { return defaultOutlierMethod; }
    public void setDefaultOutlierMethod(String defaultOutlierMethod) { this.defaultOutlierMethod = defaultOutlierMethod; }

    public int getMaxConcurrentTasks() { return maxConcurrentTasks; }
    public void setMaxConcurrentTasks(int maxConcurrentTasks) { this.maxConcurrentTasks = maxConcurrentTasks; }

    public int getResultTtlDays() { return resultTtlDays; }
    public void setResultTtlDays(int resultTtlDays) { this.resultTtlDays = resultTtlDays; }
}
