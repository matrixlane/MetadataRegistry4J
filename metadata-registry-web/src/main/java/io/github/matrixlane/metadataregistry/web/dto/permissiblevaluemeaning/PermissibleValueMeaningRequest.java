package io.github.matrixlane.metadataregistry.web.dto.permissiblevaluemeaning;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record PermissibleValueMeaningRequest(
        @NotNull(message = "permissibleValueId cannot be null")
        UUID permissibleValueId,
        @NotNull(message = "valueMeaningId cannot be null")
        UUID valueMeaningId,
        LocalDateTime validPeriodStart,
        LocalDateTime validPeriodEnd,
        /** 可选注册负载：携带时经 ItemAdministrationService 统一生成/维护 ScopedIdentifier */
        @Valid
        ItemRegistrationRequest registration) {
}
