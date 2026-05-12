package io.github.matrixlane.metadataregistry.web.entity;

/**
 * 多级审批流程实例状态枚举。
 *
 * <p>PENDING: 审批进行中（有步骤未处理完）</p>
 * <p>APPROVED: 审批已通过（所有步骤完成）</p>
 * <p>REJECTED: 审批已退回（任意步骤退回触发整体退回）</p>
 * <p>TIMEOUT: 审批已超时（某步骤超过阈值且转派失败）</p>
 */
public enum ApprovalProcessStatus {
    PENDING,
    APPROVED,
    REJECTED,
    TIMEOUT
}
