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
        UUID localEnumeratedConceptualDomainId,
        String localEnumeratedConceptualDomainLabel,
        String permittedValuesFieldname,
        UUID referencedConceptualDomainId,
        String referencedConceptualDomainLabel,
        /** value_domain_meaning（ISO/IEC 11179-31:2023 §7.4.4.1）统一视图：按子类分派的概念域关联。 */
        UUID conceptualDomainId,
        String conceptualDomainLabel,
        List<PermissibleValueResponse> permissibleValues,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta) {
}
