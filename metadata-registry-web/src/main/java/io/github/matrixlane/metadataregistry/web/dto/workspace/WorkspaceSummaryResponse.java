package io.github.matrixlane.metadataregistry.web.dto.workspace;

/**
 * 工作空间三视图计数汇总。
 */
public record WorkspaceSummaryResponse(
        long drafts,
        long pending,
        long rejected) {
}
