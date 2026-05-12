package io.github.matrixlane.metadataregistry.web.entity;

/**
 * 审批人分配规则枚举。
 *
 * <p>BY_USER: 按指定用户分配</p>
 * <p>BY_ROLE: 按角色分配（由具有指定角色的用户审批）</p>
 * <p>BY_ORGANIZATION: 按组织分配（由指定组织内的用户审批，预留扩展）</p>
 */
public enum ApprovalStepAssigneeRule {
    BY_USER,
    BY_ROLE,
    BY_ORGANIZATION
}
