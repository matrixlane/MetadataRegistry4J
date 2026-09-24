package io.github.matrixlane.metadataregistry.web.dto.unitofmeasure;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.MeasureClassRef;

import java.util.List;
import java.util.UUID;

public record UnitOfMeasureResponse(
        UUID id,
        String label,
        String sourceUri,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta,
        /** 关联的计量类。 */
        List<MeasureClassRef> measureClasses) {
}
