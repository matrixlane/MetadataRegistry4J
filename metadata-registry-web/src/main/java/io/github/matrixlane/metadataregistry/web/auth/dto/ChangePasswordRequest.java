package io.github.matrixlane.metadataregistry.web.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        // oldPassword is optional: not required for emergency login, temp password login, or first-time admin login.
        // The service layer validates it contextually.
        String oldPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "新密码长度需在 8-64 个字符之间")
        String newPassword) {
}
