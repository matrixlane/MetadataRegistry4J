package io.github.matrixlane.metadataregistry.web.dto.conceptualdomain;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

public record ConceptualDomainResponse(
        UUID id,
        String label,
        ConceptualDomainType type,
        String sourceUri,
        String permittedValuesFieldname,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        List<ValueMeaningResponse> valueMeanings,
        ItemMetaResponse meta) {
}
