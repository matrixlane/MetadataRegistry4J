package io.github.matrixlane.metadataregistry.web.assistant.dto;

import io.github.matrixlane.metadataregistry.web.assistant.AssistantJob;
import io.github.matrixlane.metadataregistry.web.assistant.Selection;
import io.github.matrixlane.metadataregistry.web.inference.InferenceJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI 交互调试记录视图：一次协作作业发给提供方的提示词原文与收到的响应原文
 * （仅存内存、随作业 TTL 清理，不落库）。不回灌既有作业响应结构。
 */
@Schema(description = "AI 交互调试记录")
public record ExchangeView(
        UUID jobId,
        String taskId,
        UUID itemId,
        Selection selection,
        String providerId,
        String providerDisplayName,
        InferenceJobStatus status,
        String prompt,
        String rawOutput,
        String error,
        LocalDateTime createdAt,
        LocalDateTime finishedAt) {

    public static ExchangeView of(AssistantJob job) {
        return new ExchangeView(
                job.getId(),
                job.getTaskId(),
                job.getItemId(),
                job.getSelection(),
                job.getProviderId(),
                job.getProviderDisplayName(),
                job.getStatus(),
                job.getPrompt(),
                job.getRawOutput(),
                job.getError(),
                job.getCreatedAt(),
                job.getFinishedAt());
    }
}
