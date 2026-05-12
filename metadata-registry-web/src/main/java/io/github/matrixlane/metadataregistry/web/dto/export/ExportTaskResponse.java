package io.github.matrixlane.metadataregistry.web.dto.export;

import io.github.matrixlane.metadataregistry.web.entity.ExportTask;
import io.github.matrixlane.metadataregistry.web.entity.ExportTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 导出任务状态响应
 */
@Schema(description = "导出任务状态")
public record ExportTaskResponse(

        @Schema(description = "任务ID")
        UUID id,

        @Schema(description = "任务状态")
        ExportTaskStatus status,

        @Schema(description = "已处理记录数")
        int processedCount,

        @Schema(description = "总记录数")
        int totalCount,

        @Schema(description = "任务创建时间")
        LocalDateTime createdAt,

        @Schema(description = "任务完成时间")
        LocalDateTime completedAt,

        @Schema(description = "任务过期时间")
        LocalDateTime expiresAt,

        @Schema(description = "错误信息（失败时）")
        String errorMessage,

        @Schema(description = "导出文件大小（字节，仅完成后）")
        long fileSize
) {
    public static ExportTaskResponse from(ExportTask task) {
        return new ExportTaskResponse(
                task.getId(),
                task.getStatus(),
                task.getProcessedCount(),
                task.getTotalCount(),
                task.getCreatedAt(),
                task.getCompletedAt(),
                task.getExpiresAt(),
                task.getErrorMessage(),
                0
        );
    }

    public static ExportTaskResponse from(ExportTask task, long fileSize) {
        return new ExportTaskResponse(
                task.getId(),
                task.getStatus(),
                task.getProcessedCount(),
                task.getTotalCount(),
                task.getCreatedAt(),
                task.getCompletedAt(),
                task.getExpiresAt(),
                task.getErrorMessage(),
                fileSize
        );
    }
}
