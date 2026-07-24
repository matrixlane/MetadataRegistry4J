package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;

/**
 * ISO/IEC 11179-3:2023, 9.6.1 — Degree_of_Conformance enumeration.
 *
 * <p>Values for specifying whether a registry is "conforming" or "strictly conforming"
 * to a referenced standard.</p>
 */
@StandardReference("ISO/IEC 11179-3:2023, 9.6.1")
public enum DegreeOfConformance {
    CONFORMING,
    STRICTLY_CONFORMING
}
