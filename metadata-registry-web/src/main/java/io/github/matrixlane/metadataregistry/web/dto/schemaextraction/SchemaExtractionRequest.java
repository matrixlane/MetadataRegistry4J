package io.github.matrixlane.metadataregistry.web.dto.schemaextraction;

/**
 * Request to execute schema extraction from a registered connection.
 */
public record SchemaExtractionRequest(
        String catalog,
        String schema,
        String tableNamePattern
) {
}
