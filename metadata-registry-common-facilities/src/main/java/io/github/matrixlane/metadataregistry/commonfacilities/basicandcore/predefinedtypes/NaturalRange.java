package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ISO/IEC 11179-3:2023, 6.2.6 — Natural_Range
 *
 * <p>A range of "natural numbers", i.e. the positive integers including zero.
 * Any instance is one of:</p>
 * <ul>
 *   <li>a constant non-negative integer (only {@code min} specified);</li>
 *   <li>a bounded range defined by a minimum and a (strictly larger) maximum;</li>
 *   <li>an unbounded range defined by only a non-negative minimum (e.g. 0..*, 1..*).</li>
 * </ul>
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.2.6")
@Embeddable
@Getter
@NoArgsConstructor
public class NaturalRange {

    /** 必备,非负。 */
    private long min;

    /** 可无;若指定,必须严格大于 {@code min}。 */
    private Long max;

    public void setMin(long min) {
        if (min < 0) {
            throw new IllegalArgumentException("Natural_Range min 必须为非负整数: " + min);
        }
        if (this.max != null && this.max <= min) {
            throw new IllegalArgumentException(
                    "Natural_Range max(" + this.max + ") 必须严格大于 min(" + min + ")");
        }
        this.min = min;
    }

    public void setMax(Long max) {
        if (max != null && max <= this.min) {
            throw new IllegalArgumentException(
                    "Natural_Range max(" + max + ") 必须严格大于 min(" + this.min + ")");
        }
        this.max = max;
    }
}
