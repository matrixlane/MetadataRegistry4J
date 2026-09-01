package io.github.matrixlane.metadataregistry.ext.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceGenerationStrategyTest {

    private final SequenceGenerationStrategy strategy = new SequenceGenerationStrategy();

    @Test
    void shouldReturnCorrectType() {
        assertThat(strategy.getType()).isEqualTo("SEQUENCE");
    }

    @Test
    void shouldGeneratePlaceholderIdentifier() {
        String id = strategy.generate(null);
        assertThat(id).startsWith("SEQ-");
        assertThat(id).hasSize(12); // "SEQ-" (4) + 8 hex chars
    }

    @Test
    void shouldHaveCorrectDefaults() {
        SequenceGenerationStrategy.Config config = new SequenceGenerationStrategy.Config();
        assertThat(config.getPrefix()).isEqualTo("");
        assertThat(config.getStart()).isEqualTo(1);
        assertThat(config.getPad()).isEqualTo(6);
        assertThat(config.getIncrementBy()).isEqualTo(1);
    }

    @Test
    void shouldResolvePrefixWithDatePlaceholders() {
        SequenceGenerationStrategy.Config config = new SequenceGenerationStrategy.Config();
        config.setPrefix("{yyyyMM}");
        config.setStart(1);
        config.setPad(4);

        String resolved = config.resolvePrefix();
        assertThat(resolved).matches("^\\d{6}$"); // yyyyMM format
    }

    @Test
    void shouldResolvePrefixWithMixedContent() {
        SequenceGenerationStrategy.Config config = new SequenceGenerationStrategy.Config();
        config.setPrefix("DE-{yyyy}");
        config.setStart(1);
        config.setPad(4);

        String resolved = config.resolvePrefix();
        assertThat(resolved).startsWith("DE-");
        assertThat(resolved).contains(String.valueOf(java.time.LocalDate.now().getYear()));
    }

    @Test
    void shouldHandleNullPrefix() {
        SequenceGenerationStrategy.Config config = new SequenceGenerationStrategy.Config();
        config.setPrefix(null);

        String resolved = config.resolvePrefix();
        assertThat(resolved).isEmpty();
    }
}
