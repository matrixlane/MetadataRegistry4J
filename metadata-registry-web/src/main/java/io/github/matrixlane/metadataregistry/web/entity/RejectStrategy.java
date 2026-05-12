package io.github.matrixlane.metadataregistry.web.entity;

/**
 * 审批退回策略枚举。
 *
 * <p>REJECT_ALL: 整体退回（退回至提交人，重新提交后从头开始）</p>
 * <p>REJECT_TO_PREV: 逐级退回（退回至上一步，重新提交后从该步开始，预留扩展）</p>
 */
public enum RejectStrategy {
    REJECT_ALL,
    REJECT_TO_PREV
}
