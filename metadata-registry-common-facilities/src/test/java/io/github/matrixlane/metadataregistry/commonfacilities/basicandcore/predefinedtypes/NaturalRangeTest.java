package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 11179-3:2023, 6.2.6:min 必备非负;max 可无,若指定必须严格大于 min。
 */
class NaturalRangeTest {

    @Test
    void shouldSetMinMax() {
        NaturalRange nr = new NaturalRange();
        nr.setMin(10L);
        nr.setMax(100L);
        assertThat(nr.getMin()).isEqualTo(10L);
        assertThat(nr.getMax()).isEqualTo(100L);
    }

    @Test
    void maxMayBeNull_unboundedRange() {
        // 0..*、1..* 等无界区间只有非负 min
        NaturalRange nr = new NaturalRange();
        nr.setMin(0L);
        assertThat(nr.getMax()).isNull();
    }

    @Test
    void minMustBeNonNegative() {
        NaturalRange nr = new NaturalRange();
        assertThatThrownBy(() -> nr.setMin(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxMustBeStrictlyGreaterThanMin() {
        NaturalRange nr = new NaturalRange();
        nr.setMin(10L);
        assertThatThrownBy(() -> nr.setMax(10L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nr.setMax(5L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loweringMinBelowExistingMaxIsRejected() {
        NaturalRange nr = new NaturalRange();
        nr.setMin(1L);
        nr.setMax(5L);
        assertThatThrownBy(() -> nr.setMin(5L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
