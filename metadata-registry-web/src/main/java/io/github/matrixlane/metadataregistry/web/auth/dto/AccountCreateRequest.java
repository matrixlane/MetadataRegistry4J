package io.github.matrixlane.metadataregistry.web.auth.dto;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_\\-.]{3,32}$", message = "用户名仅可包含字母数字_-.，长度 3-32")
        String username,
        @NotBlank @Size(min = 8, max = 64)
        String password,
        SystemRole sysRole,
        Boolean enabled) {
}
