package io.github.matrixlane.metadataregistry.web.auth.dto;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record AccountProfileResponse(
        UUID id,
        String username,
        SystemRole sysRole,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        UUID individualId,
        boolean isRegistrar,
        boolean forceChangePassword,
        boolean emergencyLogin,
        boolean tempPasswordUsed) {
}
