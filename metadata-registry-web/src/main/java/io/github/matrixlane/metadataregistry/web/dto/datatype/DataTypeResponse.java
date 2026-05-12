package io.github.matrixlane.metadataregistry.web.dto.datatype;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

public record DataTypeResponse(
        UUID id,
        String label,
        UUID dataTypeSchemeId,
        String dataTypeSchemeLabel,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta) {
}
