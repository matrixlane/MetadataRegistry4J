package io.github.matrixlane.metadataregistry.web.dto.dec;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

public record DataElementConceptResponse(
        UUID id,
        String label,
        UUID objectClassId,
        String objectClassLabel,
        UUID propertyId,
        String propertyLabel,
        String sourceUri,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta) {
}
