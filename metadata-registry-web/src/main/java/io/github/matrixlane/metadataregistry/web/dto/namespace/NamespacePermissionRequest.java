package io.github.matrixlane.metadataregistry.web.dto.namespace;

import io.github.matrixlane.metadataregistry.web.entity.NamespacePermissionType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record NamespacePermissionRequest(
    UUID appUserId,
    UUID organizationId,
    @NotNull(message = "权限类型不能为空")
    NamespacePermissionType permissionType
) {}
