package io.github.matrixlane.metadataregistry.schemaextraction.exception;

/**
 * Exception thrown when schema extraction fails.
 */
public class SchemaExtractionException extends RuntimeException {

    public SchemaExtractionException(String message) {
        super(message);
    }

    public SchemaExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
