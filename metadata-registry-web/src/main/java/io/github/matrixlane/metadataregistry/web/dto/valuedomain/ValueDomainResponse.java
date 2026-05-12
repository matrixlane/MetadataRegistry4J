package io.github.matrixlane.metadataregistry.web.dto.valuedomain;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ValueDomainResponse(
        UUID id,
        String label,
        ValueDomainType type,
        UUID dataTypeId,
        String dataTypeLabel,
        UUID unitOfMeasureId,
        String unitOfMeasureLabel,
        Integer maximumCharacterQuantity,
        String format,
        LocalDateTime validPeriodStart,
        LocalDateTime validPeriodEnd,
        UUID describedConceptualDomainId,
        String describedConceptualDomainLabel,
        List<PermissibleValueResponse> permissibleValues,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta) {
}
