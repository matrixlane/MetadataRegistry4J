package io.github.matrixlane.metadataregistry.web.dto.namespace;

public record DeletionCheckResponse(
    boolean hasIdentifiers,
    int count,
    String message
) {}
