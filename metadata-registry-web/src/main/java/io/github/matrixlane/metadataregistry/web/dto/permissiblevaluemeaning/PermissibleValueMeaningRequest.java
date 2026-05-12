package io.github.matrixlane.metadataregistry.web.dto.permissiblevaluemeaning;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record PermissibleValueMeaningRequest(
        @NotNull(message = "permissibleValueId cannot be null")
        UUID permissibleValueId,
        @NotNull(message = "valueMeaningId cannot be null")
        UUID valueMeaningId,
        LocalDateTime validPeriodStart,
        LocalDateTime validPeriodEnd) {
}
