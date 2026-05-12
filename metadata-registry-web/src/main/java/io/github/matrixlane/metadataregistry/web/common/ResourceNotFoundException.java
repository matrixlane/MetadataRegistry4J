package io.github.matrixlane.metadataregistry.web.common;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("%s not found: %s".formatted(resource, id));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
