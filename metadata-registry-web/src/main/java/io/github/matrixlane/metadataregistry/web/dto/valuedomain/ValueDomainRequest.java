package io.github.matrixlane.metadataregistry.web.dto.valuedomain;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ValueDomainRequest(
        ValueDomainType type,
        UUID dataTypeId,
        UUID unitOfMeasureId,
        Integer maximumCharacterQuantity,
        String format,
        LocalDateTime validPeriodStart,
        LocalDateTime validPeriodEnd,
        UUID describedConceptualDomainId,
        @Valid
        List<PermissibleValueEntry> permissibleValues,
        @NotEmpty(message = "designations 不能为空") @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration) {
}
