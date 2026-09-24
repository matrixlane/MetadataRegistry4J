package io.github.matrixlane.metadataregistry.web.dto.measureclass;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.DimensionalityRef;

import java.util.List;
import java.util.UUID;

public record MeasureClassResponse(
        UUID id,
        String label,
        String sourceUri,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta,
        /** 关联的维度。 */
        List<DimensionalityRef> dimensionalities) {
}
