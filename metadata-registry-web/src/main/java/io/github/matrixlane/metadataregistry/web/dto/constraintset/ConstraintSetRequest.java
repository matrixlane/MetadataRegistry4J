package io.github.matrixlane.metadataregistry.web.dto.constraintset;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;

import java.util.List;

public record ConstraintSetRequest(
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration,
        Boolean itemShallBeDesignatedIndicator,
        Boolean itemShallBeDefinedIndicator,
        Boolean itemShallBeClassifiedIndicator) {
}
