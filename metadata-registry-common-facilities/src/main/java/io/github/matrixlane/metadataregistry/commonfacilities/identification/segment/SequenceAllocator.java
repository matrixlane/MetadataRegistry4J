package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment;

/**
 * Allocates the next value of a sequence counter, scoped by a scope key.
 *
 * <p>Implementations must guarantee atomicity (database row-level pessimistic
 * lock in production). A blank scope key means namespace-level global counting.
 */
@FunctionalInterface
public interface SequenceAllocator {

    /**
     * @param scopeKey  scope key composed of referenced segment values; blank = namespace-global
     * @param start     first value ever allocated for this scope
     * @param increment allocation step
     * @param pad       zero-padding length (also defines the overflow ceiling)
     * @return the next sequence value for the scope
     * @throws SequenceOverflowException when the next value exceeds the pad ceiling
     */
    long allocate(String scopeKey, long start, int increment, int pad);
}
