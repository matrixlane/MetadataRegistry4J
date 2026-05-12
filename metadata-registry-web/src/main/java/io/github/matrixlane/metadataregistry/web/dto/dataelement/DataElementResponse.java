package io.github.matrixlane.metadataregistry.web.dto.dataelement;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

public record DataElementResponse(
        UUID id,
        String label,
        UUID dataElementConceptId,
        String dataElementConceptLabel,
        UUID valueDomainId,
        String valueDomainLabel,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta) {
}
