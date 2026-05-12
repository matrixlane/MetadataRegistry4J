package io.github.matrixlane.metadataregistry.web.dto.permissiblevaluemeaning;

import java.time.LocalDateTime;
import java.util.UUID;

public record PermissibleValueMeaningResponse(
        UUID id,
        UUID permissibleValueId,
        String permissibleValueLabel,
        UUID valueMeaningId,
        String valueMeaningLabel,
        LocalDateTime validPeriodStart,
        LocalDateTime validPeriodEnd) {
}
