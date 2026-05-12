package io.github.matrixlane.metadataregistry.web.dto.valuedomain;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PermissibleValueResponse(
        UUID id,
        String label,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        LocalDateTime validPeriodStart,
        LocalDateTime validPeriodEnd,
        ItemMetaResponse meta) {
}
