package io.github.matrixlane.metadataregistry.web.dto.namespace;

import io.github.matrixlane.metadataregistry.web.entity.NamespacePermissionType;
import java.util.UUID;

public record NamespacePermissionResponse(
    UUID id,
    UUID namespaceId,
    UUID appUserId,
    String username,
    String displayName,
    UUID organizationId,
    String organizationName,
    NamespacePermissionType permissionType
) {}
