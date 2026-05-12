package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 6.2.6 — Natural_Range
 *
 * <p>A range of natural numbers, expressed as a minimum and maximum.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.6")
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class NaturalRange {

    private long max;

    private Long min;
}
