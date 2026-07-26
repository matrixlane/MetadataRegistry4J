package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceRegistryTest {

    @Test
    void shouldTrackSequenceState() {
        SequenceRegistry registry = new SequenceRegistry(new Namespace());
        registry.setCurrentValue(5L);
        registry.setIncrementBy(2);
        registry.setStartValue(1L);
        registry.setPadLength(4);
        registry.setPrefix("ITEM");

        assertThat(registry.getCurrentValue()).isEqualTo(5L);
        assertThat(registry.getIncrementBy()).isEqualTo(2);
        assertThat(registry.getStartValue()).isEqualTo(1L);
        assertThat(registry.getPadLength()).isEqualTo(4);
        assertThat(registry.getPrefix()).isEqualTo("ITEM");
    }

    @Test
    void shouldDefaultIncrementAndStart() {
        SequenceRegistry registry = new SequenceRegistry(new Namespace());
        assertThat(registry.getIncrementBy()).isEqualTo(1);
        assertThat(registry.getStartValue()).isEqualTo(1L);
        assertThat(registry.getPadLength()).isEqualTo(6);
    }
}
