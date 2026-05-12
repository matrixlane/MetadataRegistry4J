package io.github.matrixlane.metadataregistry.web.entity;

/**
 * 审批结果枚举。
 *
 * <p>PENDING: 待审批（已提交，等待审批人处理）</p>
 * <p>APPROVED: 已通过（审批人批准）</p>
 * <p>REJECTED: 已退回（审批人退回，附带审批意见）</p>
 */
public enum ApprovalResult {
    PENDING,
    APPROVED,
    REJECTED
}
