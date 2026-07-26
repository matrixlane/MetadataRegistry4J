package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierStrategyConfigTest {

    @Test
    void shouldDefaultToDraftStatus() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        assertThat(config.getStatus()).isEqualTo(IdentifierStrategyConfig.StrategyStatus.DRAFT);
    }

    @Test
    void shouldBeEffectiveWhenPublishedAndNoEndDate() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);
        config.setEffectiveFrom(LocalDateTime.now().minusDays(1));
        assertThat(config.isEffective()).isTrue();
    }

    @Test
    void shouldNotBeEffectiveWhenDisabled() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.DISABLED);
        assertThat(config.isEffective()).isFalse();
    }

    @Test
    void shouldNotBeEffectiveBeforeEffectiveFrom() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);
        config.setEffectiveFrom(LocalDateTime.now().plusDays(1));
        assertThat(config.isEffective()).isFalse();
    }

    @Test
    void shouldNotBeEffectiveAfterEffectiveUntil() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);
        config.setEffectiveFrom(LocalDateTime.now().minusDays(2));
        config.setEffectiveUntil(LocalDateTime.now().minusDays(1));
        assertThat(config.isEffective()).isFalse();
    }
}
