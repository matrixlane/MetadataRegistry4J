package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NaturalRangeTest {

    @Test
    void shouldSetMinMax() {
        NaturalRange nr = new NaturalRange();
        nr.setMax(100L);
        nr.setMin(10L);
        assertThat(nr.getMax()).isEqualTo(100L);
        assertThat(nr.getMin()).isEqualTo(10L);
    }

    @Test
    void minMayBeNull() {
        NaturalRange nr = new NaturalRange();
        nr.setMax(50L);
        nr.setMin(null);
        assertThat(nr.getMin()).isNull();
    }
}
