package io.github.matrixlane.metadataregistry.web.dto.measureclass;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

public record MeasureClassRequest(
        String sourceUri,
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration,
        /** measure_class_dimensionality：计量类侧 0..*，空集=清空关联。 */
        List<UUID> dimensionalityIds) {
}
