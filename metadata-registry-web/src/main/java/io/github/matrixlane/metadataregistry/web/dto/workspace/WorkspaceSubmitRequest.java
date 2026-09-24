package io.github.matrixlane.metadataregistry.web.dto.workspace;

import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionInfo;

import java.util.List;
import java.util.UUID;

/**
 * 工作空间批量提交请求。
 */
public record WorkspaceSubmitRequest(
        List<UUID> itemIds,
        SubmissionInfo submission) {
}
