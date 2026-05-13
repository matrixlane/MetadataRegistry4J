package io.github.matrixlane.metadataregistry.schemaextraction.javasource;

/**
 * Exception thrown when Java source parsing fails.
 */
public class JavaSourceParseException extends RuntimeException {

    public JavaSourceParseException(String message) {
        super(message);
    }

    public JavaSourceParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
