package io.github.matrixlane.metadataregistry.web.dto.administereditem;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import jakarta.validation.constraints.NotNull;

public record StateTransitionRequest(
        @NotNull(message = "目标状态 to 不能为空") RegistrationStatus to,
        String administrativeNote) {
}
