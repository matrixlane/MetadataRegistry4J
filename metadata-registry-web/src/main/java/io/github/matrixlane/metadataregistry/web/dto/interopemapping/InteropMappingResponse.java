package io.github.matrixlane.metadataregistry.web.dto.interopemapping;

import io.github.matrixlane.metadataregistry.itemmapping.enums.InteropMappingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for an interoperability mapping result.
 */
public record InteropMappingResponse(

        UUID id,

        UUID interopDataElementId,

        String interopDataElementName,

        UUID localItemId,

        String localItemName,

        BigDecimal confidenceScore,

        String strategy,

        InteropMappingStatus status,

        String reason,

        UUID confirmedBy,

        LocalDateTime confirmedAt,

        UUID batchOperationId,

        Long version,

        LocalDateTime createdAt
) {
}
