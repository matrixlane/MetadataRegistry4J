package io.github.matrixlane.metadataregistry.web.assistant.dto;

import io.github.matrixlane.metadataregistry.ext.generation.AiGenerationRecord;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 生成来源记录响应：一次 AI 生成内容的溯源信息（任务、Agent、时间、操作者、动作）。
 */
@Schema(description = "生成来源记录")
public record GenerationRecordResponse(
        UUID id,
        UUID itemId,
        String taskId,
        String providerId,
        String agentDisplayName,
        LocalDateTime generatedAt,
        String operator,
        String outputKind,
        String rawOutput,
        String actionType) {

    public static GenerationRecordResponse of(AiGenerationRecord record) {
        return new GenerationRecordResponse(
                record.getId(),
                record.getAdministeredItem() == null || record.getAdministeredItem().getItem() == null
                        ? null : record.getAdministeredItem().getItem().getId(),
                record.getTaskId(),
                record.getProviderId(),
                record.getAgentDisplayName(),
                record.getGeneratedAt(),
                record.getOperator(),
                record.getOutputKind(),
                record.getRawOutput(),
                record.getActionType());
    }
}
