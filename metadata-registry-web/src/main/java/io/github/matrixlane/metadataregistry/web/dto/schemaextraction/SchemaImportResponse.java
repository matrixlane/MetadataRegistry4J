package io.github.matrixlane.metadataregistry.web.dto.schemaextraction;

import java.util.List;
import java.util.UUID;

/**
 * Result of importing extracted schema items into the metadata registry.
 */
public record SchemaImportResponse(
        UUID sessionId,
        int importedDecCount,
        int importedDeCount,
        int importedVdCount,
        List<String> errors
) {
}
