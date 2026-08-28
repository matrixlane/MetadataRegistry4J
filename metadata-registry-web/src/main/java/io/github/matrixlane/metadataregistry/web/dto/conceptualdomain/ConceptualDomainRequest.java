package io.github.matrixlane.metadataregistry.web.dto.conceptualdomain;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;

import java.util.List;

public record ConceptualDomainRequest(
        ConceptualDomainType type,
        String sourceUri,
        String permittedValuesFieldname,
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        List<ValueMeaningEntry> valueMeanings,
        @Valid
        ItemRegistrationRequest registration) {
}
