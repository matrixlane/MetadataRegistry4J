package io.github.matrixlane.metadataregistry.web.entity;

/**
 * 审批步骤实例状态枚举。
 *
 * <p>PENDING: 待审批（已分配，等待审批人处理）</p>
 * <p>APPROVED: 已通过（审批人批准，自动流转下一级）</p>
 * <p>REJECTED: 已退回（审批人退回，触发整体退回）</p>
 * <p>TIMEOUT: 已超时（超过阈值未处理）</p>
 * <p>TRANSFERRED: 已转派（超时后转派给其他人）</p>
 * <p>SKIPPED: 已跳过（预留扩展）</p>
 */
public enum ApprovalStepStatus {
    PENDING,
    APPROVED,
    REJECTED,
    TIMEOUT,
    TRANSFERRED,
    SKIPPED
}
