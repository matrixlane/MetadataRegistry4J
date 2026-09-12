package io.github.matrixlane.metadataregistry.web.assistant.dto;

import io.github.matrixlane.metadataregistry.web.assistant.AssistantJob;
import io.github.matrixlane.metadataregistry.web.assistant.Selection;
import io.github.matrixlane.metadataregistry.web.inference.InferenceJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 协作作业状态响应。result 按任务输出种类多态定型：
 * CANDIDATES → 候选数组；TEXT → 单段文本。degraded 标注降级语境（未关联注册项目）。
 */
@Schema(description = "协作作业状态")
public record AssistantJobResponse(
        UUID id,
        String taskId,
        UUID itemId,
        Selection selection,
        InferenceJobStatus status,
        Object result,
        boolean degraded,
        String error,
        String providerId,
        String providerDisplayName,
        LocalDateTime createdAt,
        LocalDateTime finishedAt) {

    public static AssistantJobResponse of(AssistantJob job) {
        return new AssistantJobResponse(
                job.getId(),
                job.getTaskId(),
                job.getItemId(),
                job.getSelection(),
                job.getStatus(),
                job.getResult(),
                job.isDegraded(),
                job.getError(),
                job.getProviderId(),
                job.getProviderDisplayName(),
                job.getCreatedAt(),
                job.getFinishedAt());
    }
}
