package io.github.matrixlane.metadataregistry.web.dto.registration;

/**
 * 推进流程：驳回进行中的 RECORDED→QUALIFIED 申请。
 */
public record TransitionRejectRequest(String note) {
}
