package io.github.matrixlane.metadataregistry.web.dto.workspace;

import java.util.UUID;

/**
 * 工作空间批量提交逐条结果。
 */
public record WorkspaceSubmitResult(
        UUID itemId,
        boolean success,
        Integer errorCode,
        String message) {
}
