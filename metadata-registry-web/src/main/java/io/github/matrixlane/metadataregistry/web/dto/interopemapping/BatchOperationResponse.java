package io.github.matrixlane.metadataregistry.web.dto.interopemapping;

import java.util.UUID;

/**
 * Response DTO for a batch operation.
 */
public record BatchOperationResponse(

        UUID batchOperationId,

        int affectedCount,

        String operationType
) {
}
