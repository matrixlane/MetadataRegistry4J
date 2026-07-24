package io.github.matrixlane.metadataregistry.web.auth.dto;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountUpdateRequest(
        SystemRole sysRole,
        @NotNull
        Boolean enabled,
        @Size(min = 8, max = 64, message = "新密码长度需在 8-64 个字符之间")
        String newPassword) {
}
