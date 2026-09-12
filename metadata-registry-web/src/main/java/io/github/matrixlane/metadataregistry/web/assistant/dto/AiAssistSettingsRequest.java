package io.github.matrixlane.metadataregistry.web.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 保存 AI 辅助设置请求体。candidateCount 为每字段生成候选数（1-5，默认 3）；
 * hotkeys 为快捷键绑定（命令 id → 绑定，如 Ctrl+Shift+D），保存后与默认绑定合并下发。
 */
@Schema(description = "保存 AI 辅助设置请求")
public record AiAssistSettingsRequest(
        @Schema(description = "每字段生成候选数（1-5）", example = "3")
        @NotNull(message = "candidateCount 不能为空")
        Integer candidateCount,

        @Schema(description = "快捷键绑定（命令 id → 绑定）")
        Map<String, String> hotkeys) {
}
