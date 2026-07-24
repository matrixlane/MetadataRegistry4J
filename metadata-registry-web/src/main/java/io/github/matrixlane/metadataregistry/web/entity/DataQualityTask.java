package io.github.matrixlane.metadataregistry.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 数据质量评估异步任务.
 *
 * <p>当评估的表较大时（默认 > 10000 行），
 * 创建评估任务并异步执行，用户可通过任务 ID 查询进度和结果。
 *
 * <p>评估结果以 JSON 形式存储在 resultJson 字段中，
 * 不作为 MDR 核心领域模型持久化（遵循"轻量级初评"约束）。
 */
@Entity
@Table(name = "data_quality_task")
public class DataQualityTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataQualityTaskStatus status;

    @Column(name = "progress_pct", nullable = false)
    private int progressPct;

    @Column(name = "step_name", length = 100)
    private String stepName;

    @Column(nullable = false, length = 255)
    private String tableName;

    @Column(length = 50)
    private String schemaName;

    @Column(name = "data_source_key", nullable = false, length = 100)
    private String dataSourceKey;

    @Lob
    @Column(name = "request_params")
    private String requestParams;

    @Lob
    @Column(name = "result_json")
    private String resultJson;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private UUID createdBy;

    public DataQualityTask() {}

    // ── Builder ─────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private DataQualityTaskStatus status;
        private int progressPct;
        private String stepName;
        private String tableName;
        private String schemaName;
        private String dataSourceKey;
        private String requestParams;
        private String resultJson;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
        private UUID createdBy;

        Builder() {}

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder status(DataQualityTaskStatus status) { this.status = status; return this; }
        public Builder progressPct(int progressPct) { this.progressPct = progressPct; return this; }
        public Builder stepName(String stepName) { this.stepName = stepName; return this; }
        public Builder tableName(String tableName) { this.tableName = tableName; return this; }
        public Builder schemaName(String schemaName) { this.schemaName = schemaName; return this; }
        public Builder dataSourceKey(String dataSourceKey) { this.dataSourceKey = dataSourceKey; return this; }
        public Builder requestParams(String requestParams) { this.requestParams = requestParams; return this; }
        public Builder resultJson(String resultJson) { this.resultJson = resultJson; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder completedAt(LocalDateTime completedAt) { this.completedAt = completedAt; return this; }
        public Builder createdBy(UUID createdBy) { this.createdBy = createdBy; return this; }

        public DataQualityTask build() {
            DataQualityTask task = new DataQualityTask();
            task.id = this.id;
            task.status = this.status;
            task.progressPct = this.progressPct;
            task.stepName = this.stepName;
            task.tableName = this.tableName;
            task.schemaName = this.schemaName;
            task.dataSourceKey = this.dataSourceKey;
            task.requestParams = this.requestParams;
            task.resultJson = this.resultJson;
            task.errorMessage = this.errorMessage;
            task.createdAt = this.createdAt;
            task.completedAt = this.completedAt;
            task.createdBy = this.createdBy;
            return task;
        }
    }

    // ── Getters & Setters ───────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public DataQualityTaskStatus getStatus() { return status; }
    public void setStatus(DataQualityTaskStatus status) { this.status = status; }

    public int getProgressPct() { return progressPct; }
    public void setProgressPct(int progressPct) { this.progressPct = progressPct; }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

    public String getDataSourceKey() { return dataSourceKey; }
    public void setDataSourceKey(String dataSourceKey) { this.dataSourceKey = dataSourceKey; }

    public String getRequestParams() { return requestParams; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}

