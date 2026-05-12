package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UuidGenerationStrategyTest {

    private final UuidGenerationStrategy strategy = new UuidGenerationStrategy();

    @Test
    void shouldGenerateValidUuid() {
        String id = strategy.generate(null);
        assertThat(id).isNotNull().isNotBlank();
        assertThat(id).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    void shouldGenerateUniqueUuids() {
        String id1 = strategy.generate(null);
        String id2 = strategy.generate(null);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void shouldReturnCorrectType() {
        assertThat(strategy.getType()).isEqualTo("UUID");
    }
}
