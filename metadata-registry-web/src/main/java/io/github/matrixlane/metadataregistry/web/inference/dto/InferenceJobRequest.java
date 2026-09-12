package io.github.matrixlane.metadataregistry.web.inference.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * 创建推断作业请求体。
 *
 * @param fields         请求推断的字段，必填非空，元素取值 definition / designation
 * @param language       目标语种（如 zh-CN），可空时默认 zh-CN
 * @param contextId      指称候选的建议上下文条目 id，可空
 * @param candidateCount 已废弃：候选数由 AI 辅助设置决定（1-5），此字段被忽略，仅保留以兼容旧请求
 * @param provider       提供方覆盖（cli 或 Agent 设置中的 id），可空时用默认配置
 */
@Schema(description = "创建推断作业请求")
public record InferenceJobRequest(
        @Schema(description = "请求推断的字段：definition / designation", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "fields 不能为空")
        List<String> fields,

        @Schema(description = "目标语种，如 zh-CN", example = "zh-CN")
        String language,

        @Schema(description = "指称候选的建议上下文条目 id")
        UUID contextId,

        @Schema(description = "已废弃：候选数由 AI 辅助设置决定，此字段被忽略", example = "3")
        Integer candidateCount,

        @Schema(description = "提供方覆盖：cli 或 Agent 设置中的 id")
        String provider) {
}
