package io.github.matrixlane.metadataregistry.web.federation;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 合并请求视图（不含懒加载代理）。
 */
public record MergeRequestView(
        UUID id,
        UUID sourceNodeId,
        String sourceNodeName,
        String sourceRai,
        UUID targetNamespaceId,
        MergeRequestStatus status,
        String bundleRef,
        String submittedBy,
        LocalDateTime submittedAt,
        String reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime mergedAt,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static MergeRequestView of(MergeRequest mr) {
        return new MergeRequestView(
                mr.getId(),
                mr.getSourceNode() == null ? null : mr.getSourceNode().getId(),
                mr.getSourceNode() == null ? null : mr.getSourceNode().getName(),
                mr.getSourceNode() == null ? null : mr.getSourceNode().getRai(),
                mr.getTargetNamespaceId(),
                mr.getStatus(),
                mr.getBundleRef(),
                mr.getSubmittedBy(),
                mr.getSubmittedAt(),
                mr.getReviewedBy(),
                mr.getReviewedAt(),
                mr.getMergedAt(),
                mr.getErrorMessage(),
                mr.getCreatedAt());
    }
}
