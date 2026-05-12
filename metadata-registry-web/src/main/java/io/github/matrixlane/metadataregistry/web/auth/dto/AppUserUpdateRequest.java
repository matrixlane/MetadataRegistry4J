package io.github.matrixlane.metadataregistry.web.auth.dto;

import io.github.matrixlane.metadataregistry.web.auth.AppUserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppUserUpdateRequest(
        @Size(max = 128)
        String displayName,
        @Email
        String email,
        @NotNull
        AppUserRole role,
        @NotNull
        Boolean enabled,
        @Size(min = 8, max = 64, message = "新密码长度需在 8-64 个字符之间")
        String newPassword) {
}
