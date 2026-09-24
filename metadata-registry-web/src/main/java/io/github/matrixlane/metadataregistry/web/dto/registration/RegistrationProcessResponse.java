package io.github.matrixlane.metadataregistry.web.dto.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.ApproverMode;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.QuorumType;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.util.List;
import java.util.UUID;

/**
 * 注册流程定义视图（registration-process-designer，API 契约基准）。
 *
 * @param authorityId  注册机构
 * @param transitions  迁移规则列表（含审核配置）
 */
public record RegistrationProcessResponse(
        UUID authorityId,
        List<TransitionRuleView> transitions) {

    /**
     * 单条迁移规则视图。
     *
     * @param from                 源状态
     * @param to                   目标状态
     * @param displayName          操作命名（空 = 标准迁移名）
     * @param requiresApproval     是否需审核
     * @param approverMode         审核人模式
     * @param quorumType           通过规则
     * @param quorumCount          AT_LEAST 时的人数
     * @param approverRegistrarIds 指定审核人集合（SELECTED_REGISTRARS）
     * @param enabled              是否启用
     */
    public record TransitionRuleView(
            RegistrationStatus from,
            RegistrationStatus to,
            String displayName,
            boolean requiresApproval,
            ApproverMode approverMode,
            QuorumType quorumType,
            Integer quorumCount,
            List<UUID> approverRegistrarIds,
            boolean enabled) {
    }
}
