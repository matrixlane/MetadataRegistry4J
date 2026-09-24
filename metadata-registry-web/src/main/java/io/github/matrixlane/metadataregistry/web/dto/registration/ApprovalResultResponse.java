package io.github.matrixlane.metadataregistry.web.dto.registration;

import java.util.List;

/**
 * 批准操作结果（registration-process-designer，API 契约基准）。
 *
 * @param executed    本次批准是否使迁移生效（达 quorum 自动生效）
 * @param transitions 该项最新的迁移动作视图（生效后为迁移后状态的可行动作）
 */
public record ApprovalResultResponse(
        boolean executed,
        List<AvailableTransitionView> transitions) {
}
