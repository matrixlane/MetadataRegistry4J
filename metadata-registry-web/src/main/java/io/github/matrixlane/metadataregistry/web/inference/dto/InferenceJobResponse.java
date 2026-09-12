package io.github.matrixlane.metadataregistry.web.inference.dto;

import io.github.matrixlane.metadataregistry.web.inference.InferenceJob;
import io.github.matrixlane.metadataregistry.web.inference.InferenceJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 推断作业状态响应（不含进程等内部引用）。
 */
@Schema(description = "推断作业状态")
public record InferenceJobResponse(
        UUID id,
        UUID itemId,
        InferenceJobStatus status,
        List<InferenceCandidate> candidates,
        String error,
        String providerId,
        String providerDisplayName,
        LocalDateTime createdAt,
        LocalDateTime finishedAt) {

    public static InferenceJobResponse of(InferenceJob job) {
        return new InferenceJobResponse(
                job.getId(),
                job.getItemId(),
                job.getStatus(),
                job.getCandidates(),
                job.getError(),
                job.getProviderId(),
                job.getProviderDisplayName(),
                job.getCreatedAt(),
                job.getFinishedAt());
    }
}
