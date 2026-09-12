package io.github.matrixlane.metadataregistry.web.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * AI 辅助设置视图：候选数与快捷键绑定（含默认绑定合并结果）。
 */
@Schema(description = "AI 辅助设置")
public record AiAssistSettingsResponse(
        @Schema(description = "每字段生成候选数（1-5）", example = "3")
        int candidateCount,

        @Schema(description = "快捷键绑定（命令 id → 绑定，含默认值合并）")
        Map<String, String> hotkeys) {
}
