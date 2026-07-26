package io.github.matrixlane.metadataregistry.commonfacilities.identification;

public interface SequenceProvider {
    long getNextValue(Namespace namespace,
                      long start, int increment, int pad, String prefix);
}
