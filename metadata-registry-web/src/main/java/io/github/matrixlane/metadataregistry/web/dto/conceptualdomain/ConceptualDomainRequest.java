package io.github.matrixlane.metadataregistry.web.dto.conceptualdomain;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConceptualDomainRequest(
        ConceptualDomainType type,
        String sourceUri,
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        List<ValueMeaningEntry> valueMeanings,
        LocalDateTime validPeriodStart,
        LocalDateTime validPeriodEnd,
        @Valid
        ItemRegistrationRequest registration,
        /** dimensionality(0..1);null 表示清除。 */
        UUID dimensionalityId) {
}
