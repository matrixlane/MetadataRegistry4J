package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 6.4.2 — Concept
 *
 * <p>A unit of knowledge created by a unique combination of characteristics.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.4.2")
@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class Concept extends Item {

    private String sourceUri;
}
