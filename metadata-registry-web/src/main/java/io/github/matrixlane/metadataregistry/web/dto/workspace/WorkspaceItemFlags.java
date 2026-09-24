package io.github.matrixlane.metadataregistry.web.dto.workspace;

/**
 * 工作空间列表行级操作标志。
 */
public record WorkspaceItemFlags(
        boolean canSubmit,
        boolean canRequestCandidate,
        boolean canRequestQualification,
        boolean canApprove,
        boolean canReject,
        boolean canRollback) {
}
