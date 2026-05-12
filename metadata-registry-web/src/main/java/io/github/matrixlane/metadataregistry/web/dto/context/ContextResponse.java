package io.github.matrixlane.metadataregistry.web.dto.context;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

public record ContextResponse(
        UUID id,
        String label,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta) {
}
