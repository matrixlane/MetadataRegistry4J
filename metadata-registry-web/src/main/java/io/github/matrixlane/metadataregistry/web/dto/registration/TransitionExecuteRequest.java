package io.github.matrixlane.metadataregistry.web.dto.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 推进流程：直接执行无需审核的迁移（registration-process-designer）。
 */
public record TransitionExecuteRequest(
        @NotNull(message = "目标状态 to 不能为空") RegistrationStatus to,
        String note) {
}
