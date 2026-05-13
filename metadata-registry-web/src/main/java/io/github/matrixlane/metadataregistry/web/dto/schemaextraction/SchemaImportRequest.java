package io.github.matrixlane.metadataregistry.web.dto.schemaextraction;

import java.util.List;
import java.util.UUID;

/**
 * Request to import selected extracted items into the metadata registry.
 */
public record SchemaImportRequest(
        UUID sessionId,
        List<String> selectedDecNames,
        List<String> selectedDeNames,
        List<String> selectedVdNames,
        boolean importAll
) {
}
