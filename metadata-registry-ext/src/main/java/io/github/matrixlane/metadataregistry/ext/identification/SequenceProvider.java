package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
public interface SequenceProvider {
    long getNextValue(Namespace namespace,
                      long start, int increment, int pad, String prefix);

    /**
     * Allocates the next value for the given scope key. Blank scope keys are
     * normalized to {@link SequenceRegistry#GLOBAL_SCOPE_KEY} (namespace-level
     * counting, identical to the legacy behavior).
     *
     * <p>The default implementation ignores the scope key so legacy providers
     * keep compiling; persistence-backed providers must override it.
     */
    default long getNextValue(Namespace namespace, String scopeKey,
                              long start, int increment, int pad) {
        return getNextValue(namespace, start, increment, pad, "");
    }
}
