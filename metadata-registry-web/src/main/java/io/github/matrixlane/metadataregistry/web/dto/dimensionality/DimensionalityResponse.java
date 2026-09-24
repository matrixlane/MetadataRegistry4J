package io.github.matrixlane.metadataregistry.web.dto.dimensionality;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

public record DimensionalityResponse(
        UUID id,
        String label,
        String sourceUri,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta,
        /** coordinate_indicator 三态：null 表示 Not applicable。 */
        Boolean coordinateIndicator,
        /** 关联的计量类。 */
        List<MeasureClassRef> measureClasses) {
}
