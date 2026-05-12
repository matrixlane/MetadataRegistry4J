package io.github.matrixlane.metadataregistry.web.auth.dto;

import io.github.matrixlane.metadataregistry.web.auth.AppUserRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        AppUserRole role,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt) {
}
