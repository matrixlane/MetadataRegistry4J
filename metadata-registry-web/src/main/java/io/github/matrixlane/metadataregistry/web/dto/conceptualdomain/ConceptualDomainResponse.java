package io.github.matrixlane.metadataregistry.web.dto.conceptualdomain;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConceptualDomainResponse(
        UUID id,
        String label,
        ConceptualDomainType type,
        String sourceUri,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        List<ValueMeaningResponse> valueMeanings,
        LocalDateTime validPeriodStart,
        LocalDateTime validPeriodEnd,
        ItemMetaResponse meta,
        /** dimensionality(0..1)。 */
        UUID dimensionalityId,
        String dimensionalityLabel) {
}
