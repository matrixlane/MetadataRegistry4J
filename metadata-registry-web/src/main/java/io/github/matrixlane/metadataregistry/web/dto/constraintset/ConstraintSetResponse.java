package io.github.matrixlane.metadataregistry.web.dto.constraintset;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

public record ConstraintSetResponse(
        UUID id,
        String label,
        Boolean itemShallBeDesignatedIndicator,
        Boolean itemShallBeDefinedIndicator,
        Boolean itemShallBeClassifiedIndicator,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta) {
}
