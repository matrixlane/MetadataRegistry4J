package io.github.matrixlane.metadataregistry.schemaextraction.exception;

/**
 * Exception thrown when database connection fails.
 */
public class ConnectionException extends RuntimeException {

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
