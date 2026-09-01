package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierGenerationStrategyRegistryTest {

    private IdentifierGenerationStrategyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = IdentifierGenerationStrategyRegistry.getInstance();
        registry.clearCache();
    }

    @Test
    void shouldFallbackToUuidWhenNoConfig() {
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        IdentifierGenerationStrategy strategy = registry.resolve(ns);
        assertThat(strategy).isInstanceOf(UuidGenerationStrategy.class);
    }

    @Test
    void shouldResolveGlobalDefaultBeforeUuidFallback() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStrategyType("SEQUENCE");
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);
        registry.setGlobalDefault(config);

        IdentifierGenerationStrategy strategy = registry.resolve(null);
        assertThat(strategy).isInstanceOf(SequenceGenerationStrategy.class);
    }

    @Test
    void shouldResolveNamespaceSpecificConfig() {
        UUID nsId = UUID.randomUUID();
        Namespace ns = new Namespace();
        ns.setId(nsId);

        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStrategyType("URN");
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);
        registry.setNamespaceConfig(nsId, config);

        IdentifierGenerationStrategy strategy = registry.resolve(ns);
        assertThat(strategy).isInstanceOf(UrnGenerationStrategy.class);
    }

    @Test
    void shouldPreferNamespaceConfigOverGlobalDefault() {
        UUID nsId = UUID.randomUUID();
        Namespace ns = new Namespace();
        ns.setId(nsId);

        IdentifierStrategyConfig global = new IdentifierStrategyConfig();
        global.setStrategyType("SEQUENCE");
        global.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);
        registry.setGlobalDefault(global);

        IdentifierStrategyConfig nsConfig = new IdentifierStrategyConfig();
        nsConfig.setStrategyType("TEMPLATE");
        nsConfig.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);
        registry.setNamespaceConfig(nsId, nsConfig);

        IdentifierGenerationStrategy strategy = registry.resolve(ns);
        assertThat(strategy).isInstanceOf(TemplateGenerationStrategy.class);
    }

    @Test
    void shouldFallbackToUuidForUnknownStrategyType() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStrategyType("UNKNOWN");
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);
        registry.setGlobalDefault(config);

        IdentifierGenerationStrategy strategy = registry.resolve(null);
        assertThat(strategy).isInstanceOf(UuidGenerationStrategy.class);
    }
}
