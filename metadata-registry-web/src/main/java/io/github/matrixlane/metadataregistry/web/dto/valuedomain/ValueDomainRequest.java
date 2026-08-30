package io.github.matrixlane.metadataregistry.web.dto.valuedomain;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;

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
        UUID localEnumeratedConceptualDomainId,
        String permittedValuesFieldname,
        UUID referencedConceptualDomainId,
        @Valid
        List<PermissibleValueEntry> permissibleValues,
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration) {
}
