package io.github.matrixlane.metadataregistry.web.dto.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

/**
 * 单个可达迁移动作的执行视图（registration-process-designer，API 契约基准）。
 *
 * @param to             目标状态
 * @param displayName    操作命名（空 = 标准迁移名）
 * @param requiresApproval 是否需审核
 * @param approvedCount  已批准人数（去重后）
 * @param quorum         通过人数（任一人 = 1）
 * @param canExecute     当前用户可否直接执行（无需审核迁移）
 * @param canApprove     当前用户可否批准（需审核迁移且为合格审核 Registrar 且未批准过）
 * @param approvedByMe   当前用户（活动 Registrar 身份）是否已批准过
 */
public record AvailableTransitionView(
        RegistrationStatus to,
        String displayName,
        boolean requiresApproval,
        long approvedCount,
        int quorum,
        boolean canExecute,
        boolean canApprove,
        boolean approvedByMe) {
}
