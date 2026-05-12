package io.github.matrixlane.metadataregistry.web.dto.conceptualdomain;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConceptualDomainRequest(
        ConceptualDomainType type,
        String sourceUri,
        @NotEmpty(message = "designations 不能为空") @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        List<ValueMeaningEntry> valueMeanings,
        @Valid
        ItemRegistrationRequest registration) {
}
