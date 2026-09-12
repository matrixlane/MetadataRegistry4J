package io.github.matrixlane.metadataregistry.web.assistant.dto;

import io.github.matrixlane.metadataregistry.web.assistant.OutputKind;
import io.github.matrixlane.metadataregistry.web.assistant.TaskInputSpec;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 协作任务清单项（GET /api/v1/assistant/tasks），前端据此动态渲染任务入口。
 */
@Schema(description = "协作任务清单项")
public record AssistantTaskView(
        @Schema(description = "任务 id")
        String id,

        @Schema(description = "展示名")
        String displayName,

        @Schema(description = "输入规格")
        TaskInputSpec inputSpec,

        @Schema(description = "输出种类")
        OutputKind outputKind) {
}
