package io.github.matrixlane.metadataregistry.web.dto.dimensionality;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

public record DimensionalityRequest(
        String sourceUri,
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration,
        /** coordinate_indicator：TRUE=坐标测量、FALSE=区间测量、NULL=非物理单位（Not applicable）。 */
        Boolean coordinateIndicator,
        /** dimensionality_measure_class 关联（0..*）。 */
        List<UUID> measureClassIds) {
}
