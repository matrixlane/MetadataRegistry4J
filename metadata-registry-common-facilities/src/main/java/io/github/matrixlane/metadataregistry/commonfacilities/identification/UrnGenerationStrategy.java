package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;

import java.util.UUID;

/**
 * Generates URN-style identifiers.
 *
 * <p>Format: {@code urn:<namespace>:<uuid>} where {@code namespace}
 * is derived from the {@link Namespace} designation (lowercase, no spaces)
 * and {@code uuid} is a random UUID.
 */
public class UrnGenerationStrategy implements IdentifierGenerationStrategy {

    @Override
    public String generate(Namespace namespace) {
        String nsPart = sanitizeNamespace(namespace);
        return "urn:" + nsPart + ":" + UUID.randomUUID().toString().toLowerCase();
    }

    @Override
    public String getType() {
        return "URN";
    }

    private String sanitizeNamespace(Namespace namespace) {
        if (namespace == null || namespace.getDesignations() == null
                || namespace.getDesignations().isEmpty()) {
            return "mdr";
        }
        Sign sign = namespace.getDesignations().get(0).getSign();
        String raw = sign == null ? "mdr" : sign.getValue();
        return raw.toLowerCase()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9\\-]", "");
    }
}
