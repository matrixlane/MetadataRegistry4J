package io.github.matrixlane.metadataregistry.schemaextraction.openapi;

import io.github.matrixlane.metadataregistry.schemaextraction.exception.SchemaExtractionException;

/**
 * Exception thrown when OpenAPI document parsing or schema extraction fails.
 */
public class OpenApiExtractionException extends SchemaExtractionException {

    public OpenApiExtractionException(String message) {
        super(message);
    }

    public OpenApiExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
