package io.github.matrixlane.metadataregistry.web.dto.initwizard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Step 5 payload: the new admin password plus confirmation.
 * Same constraints as the change-password endpoint (6-20 chars, matches
 * the dialog that used to live in AppLayout).
 */
public record InitWizardCommitRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 20, message = "新密码长度需在 6 到 20 个字符之间")
        String newPassword,

        @NotBlank(message = "确认密码不能为空")
        String confirmPassword) {
}
