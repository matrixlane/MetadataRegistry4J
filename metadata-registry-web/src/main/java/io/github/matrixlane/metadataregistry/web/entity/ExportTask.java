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
 * 异步导出任务
 *
 * <p>当导出数据量超过阈值时，创建导出任务并异步生成导出结果。
 * 用户可通过任务 ID 查询任务状态和下载链接。
 */
@Entity
@Table(name = "export_task")
public class ExportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExportTaskStatus status;

    @Column(nullable = false)
    private int processedCount;

    @Column(nullable = false)
    private int totalCount;

    @Lob
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String requestParams;

    @Column(length = 500)
    private String resultFilePath;

    @Lob
    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false, length = 10)
    private String format = "json";

    public ExportTask() {}

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public UUID getId() { return id; }
    public ExportTaskStatus getStatus() { return status; }
    public int getProcessedCount() { return processedCount; }
    public int getTotalCount() { return totalCount; }
    public String getRequestParams() { return requestParams; }
    public String getResultFilePath() { return resultFilePath; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public UUID getCreatedBy() { return createdBy; }
    public String getFormat() { return format; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setStatus(ExportTaskStatus status) { this.status = status; }
    public void setProcessedCount(int processedCount) { this.processedCount = processedCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }
    public void setResultFilePath(String resultFilePath) { this.resultFilePath = resultFilePath; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public void setFormat(String format) { this.format = format; }

    // Builder
    public static class Builder {
        private UUID id;
        private ExportTaskStatus status;
        private int processedCount;
        private int totalCount;
        private String requestParams;
        private String resultFilePath;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
        private LocalDateTime expiresAt;
        private UUID createdBy;
        private String format = "json";

        Builder() {}

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder status(ExportTaskStatus status) { this.status = status; return this; }
        public Builder processedCount(int processedCount) { this.processedCount = processedCount; return this; }
        public Builder totalCount(int totalCount) { this.totalCount = totalCount; return this; }
        public Builder requestParams(String requestParams) { this.requestParams = requestParams; return this; }
        public Builder resultFilePath(String resultFilePath) { this.resultFilePath = resultFilePath; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder completedAt(LocalDateTime completedAt) { this.completedAt = completedAt; return this; }
        public Builder expiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder createdBy(UUID createdBy) { this.createdBy = createdBy; return this; }
        public Builder format(String format) { this.format = format; return this; }

        public ExportTask build() {
            ExportTask task = new ExportTask();
            task.id = this.id;
            task.status = this.status;
            task.processedCount = this.processedCount;
            task.totalCount = this.totalCount;
            task.requestParams = this.requestParams;
            task.resultFilePath = this.resultFilePath;
            task.errorMessage = this.errorMessage;
            task.createdAt = this.createdAt;
            task.completedAt = this.completedAt;
            task.expiresAt = this.expiresAt;
            task.createdBy = this.createdBy;
            task.format = this.format;
            return task;
        }
    }
}
