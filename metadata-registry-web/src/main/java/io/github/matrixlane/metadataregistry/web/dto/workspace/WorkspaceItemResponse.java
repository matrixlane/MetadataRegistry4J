package io.github.matrixlane.metadataregistry.web.dto.workspace;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.util.UUID;

/**
 * 工作空间列表行 DTO。
 */
public record WorkspaceItemResponse(
        UUID itemId,
        String itemType,
        String label,
        RegistrationStatus registrationStatus,
        String administrativeStatus,
        String version,
        boolean hasRollbackBaseline,
        WorkspaceItemFlags flags) {
}
