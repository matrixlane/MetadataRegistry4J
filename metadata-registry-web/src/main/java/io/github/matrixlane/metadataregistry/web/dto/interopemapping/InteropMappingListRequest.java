package io.github.matrixlane.metadataregistry.web.dto.interopemapping;

import io.github.matrixlane.metadataregistry.itemmapping.enums.InteropMappingStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request parameters for listing interop mappings with filters.
 */
public record InteropMappingListRequest(

        UUID datasetId,

        InteropMappingStatus status,

        @DecimalMin("0.00")
        @DecimalMax("1.00")
        BigDecimal minConfidence,

        @DecimalMin("0.00")
        @DecimalMax("1.00")
        BigDecimal maxConfidence
) {
}
