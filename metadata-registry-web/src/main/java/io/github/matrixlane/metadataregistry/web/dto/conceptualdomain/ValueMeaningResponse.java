package io.github.matrixlane.metadataregistry.web.dto.conceptualdomain;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ValueMeaningResponse(
        UUID id,
        String label,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        LocalDateTime validPeriodStart,
        LocalDateTime validPeriodEnd,
        ItemMetaResponse meta) {
}
