package io.github.matrixlane.metadataregistry.commonfacilities.identification;

public interface SequenceProvider {
    long getNextValue(Namespace namespace, IdentifierType identifierType, 
                      long start, int increment, int pad, String prefix);
}
