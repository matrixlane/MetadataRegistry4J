package io.github.matrixlane.metadataregistry.web.dto.initwizard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Step 5 payload: the new admin password plus confirmation.
 * Constraints aligned with the change-password endpoint and other
 * password DTOs ({@link io.github.matrixlane.metadataregistry.web.auth.dto.ChangePasswordRequest},
 * {@link io.github.matrixlane.metadataregistry.web.auth.dto.AccountCreateRequest},
 * {@link io.github.matrixlane.metadataregistry.web.auth.dto.AccountUpdateRequest}):
 * 8-64 characters.
 */
public record InitWizardCommitRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "新密码长度需在 8-64 个字符之间")
        String newPassword,

        @NotBlank(message = "确认密码不能为空")
        String confirmPassword) {
}
