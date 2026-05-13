package io.github.matrixlane.metadataregistry.web.dto.interopemapping;

import java.util.UUID;

/**
 * Response DTO for alignment quality score.
 */
public record QualityScoreResponse(

        UUID datasetId,

        int totalCount,

        int matchedCount,

        int pendingCount,

        int unmatchedCount,

        double score,

        double matchedRate,

        double pendingRate,

        double unmatchedRate
) {
}
