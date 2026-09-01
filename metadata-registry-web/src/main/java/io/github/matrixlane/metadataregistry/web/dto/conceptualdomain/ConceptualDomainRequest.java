package io.github.matrixlane.metadataregistry.web.dto.conceptualdomain;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;

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
        ItemRegistrationRequest registration) {
}
