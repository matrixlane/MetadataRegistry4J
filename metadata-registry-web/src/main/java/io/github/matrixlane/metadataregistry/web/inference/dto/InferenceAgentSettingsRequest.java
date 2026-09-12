package io.github.matrixlane.metadataregistry.web.inference.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 保存 Agent 设置请求体。
 *
 * @param defaultProvider 默认提供方 id（可空，空时回退静态配置旧语义）
 * @param agents          Agent 条目：内置 id（claude/codex/kimi）仅存覆盖项，
 *                        其余 id 视为自定义条目（必须携带 command）
 */
@Schema(description = "保存推断 Agent 设置请求")
public record InferenceAgentSettingsRequest(
        @Schema(description = "默认提供方 id（须为已启用条目）")
        String defaultProvider,

        @Schema(description = "Agent 条目列表")
        List<@Valid AgentItem> agents) {

    @Schema(description = "Agent 条目")
    public record AgentItem(
            @Schema(description = "Agent id", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Agent id 不能为空")
            String id,

            @Schema(description = "展示名覆盖")
            String displayName,

            @Schema(description = "是否启用（缺省 true）")
            Boolean enabled,

            @Schema(description = "自定义命令（内置 id 为覆盖项；自定义条目必填）")
            String command,

            @Schema(description = "命令参数")
            List<String> args) {
    }
}
