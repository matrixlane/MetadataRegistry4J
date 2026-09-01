package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import java.util.UUID;

/**
 * Generates RFC-4122 random UUID identifiers.
 */
public class UuidGenerationStrategy implements IdentifierGenerationStrategy {

    @Override
    public String generate(Namespace namespace) {
        return UUID.randomUUID().toString();
    }

    @Override
    public String getType() {
        return "UUID";
    }
}
