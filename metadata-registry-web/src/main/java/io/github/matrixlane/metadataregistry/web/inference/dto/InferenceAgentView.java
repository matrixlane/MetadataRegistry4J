package io.github.matrixlane.metadataregistry.web.inference.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Agent 设置合并视图（内置定义 ⊕ 持久化覆盖 ⊕ 自定义条目），含探测状态。
 *
 * @param id          Agent id
 * @param displayName 展示名
 * @param kind        cli
 * @param command     可执行命令
 * @param args        命令参数
 * @param enabled     是否启用（仅已启用 Agent 可被推断作业选用）
 * @param available   探测是否可用
 * @param detail      探测详情/不可用原因
 * @param isDefault   是否为默认提供方
 * @param builtin     是否内置 Agent
 */
@Schema(description = "推断 Agent 合并视图")
public record InferenceAgentView(
        String id,
        String displayName,
        String kind,
        String command,
        List<String> args,
        boolean enabled,
        boolean available,
        String detail,
        boolean isDefault,
        boolean builtin) {
}
