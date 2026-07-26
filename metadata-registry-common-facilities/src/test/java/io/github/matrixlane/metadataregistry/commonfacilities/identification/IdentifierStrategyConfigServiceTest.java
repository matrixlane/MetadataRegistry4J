package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifierStrategyConfigServiceTest {

    private IdentifierStrategyConfigService service;

    @BeforeEach
    void setUp() {
        IdentifierGenerationStrategyRegistry registry = IdentifierGenerationStrategyRegistry.getInstance();
        registry.clearCache();
        service = new IdentifierStrategyConfigService(registry);
    }

    @Test
    void shouldGenerateUuidFallbackWhenNoConfig() {
        String identifier = service.generateIdentifier(null, null);
        assertThat(identifier).isNotBlank();
        assertThat(identifier).matches("^[0-9a-fA-F-]{36}$");
    }

    @Test
    void shouldGenerateUrnWhenGlobalDefaultIsUrn() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStrategyType("URN");
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);

        IdentifierGenerationStrategyRegistry registry = IdentifierGenerationStrategyRegistry.getInstance();
        registry.setGlobalDefault(config);

        String identifier = service.generateIdentifier(null, null);
        assertThat(identifier).startsWith("urn:");
    }

    @Test
    void shouldGenerateSequenceWhenConfigIsSequence() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStrategyType("SEQUENCE");
        config.setConfigJson("{\"start\":1,\"incrementBy\":1,\"pad\":6,\"prefix\":\"DE\"}");
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);

        IdentifierGenerationStrategyRegistry registry = IdentifierGenerationStrategyRegistry.getInstance();
        registry.setGlobalDefault(config);

        Namespace ns = new Namespace();
        String id1 = service.generateIdentifier(ns, null);
        String id2 = service.generateIdentifier(ns, null);
        assertThat(id1).startsWith("DE");
        assertThat(id2).isGreaterThan(id1);
    }

    @Test
    void shouldGenerateTemplateWhenConfigIsTemplate() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStrategyType("TEMPLATE");
        config.setConfigJson("{\"template\":\"TEST-{seq:4}\",\"prefix\":\"TEST\"}");
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);

        IdentifierGenerationStrategyRegistry registry = IdentifierGenerationStrategyRegistry.getInstance();
        registry.setGlobalDefault(config);

        IdentifierStrategyConfigService.GenerationContext ctx =
                IdentifierStrategyConfigService.GenerationContext.ofSeq(7L);
        String identifier = service.generateIdentifier(null, ctx);
        assertThat(identifier).isEqualTo("TEST-0007");
    }
}
