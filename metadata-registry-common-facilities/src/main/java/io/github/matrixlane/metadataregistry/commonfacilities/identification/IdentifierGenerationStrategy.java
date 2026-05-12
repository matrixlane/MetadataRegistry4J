package io.github.matrixlane.metadataregistry.commonfacilities.identification;

/**
 * Strategy interface for generating business identifiers.
 *
 * <p>Implementations must produce unique, stable identifiers within the
 * scope of a single {@link Namespace}.
 */
public interface IdentifierGenerationStrategy {

    /**
     * Generates a new business identifier.
     *
     * @param namespace the namespace context (may be used for prefix/suffix)
     * @return a non-null, non-blank identifier string
     */
    String generate(Namespace namespace);

    /**
     * Returns the strategy type name (e.g. "UUID", "URN").
     */
    String getType();
}
