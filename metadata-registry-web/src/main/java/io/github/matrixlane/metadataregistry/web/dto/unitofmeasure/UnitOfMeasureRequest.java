package io.github.matrixlane.metadataregistry.web.dto.unitofmeasure;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

public record UnitOfMeasureRequest(
        String sourceUri,
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration,
        /** unit_of_measure_measure_class：UoM 侧 1..*，必须至少归属一个计量类。 */
        List<UUID> measureClassIds) {
}
