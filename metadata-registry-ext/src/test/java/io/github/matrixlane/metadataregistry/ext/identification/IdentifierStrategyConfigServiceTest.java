package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
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

    @Test
    void shouldAbbreviateItemTypeFromClassSimpleName() {
        assertThat(IdentifierStrategyConfigService.itemTypeAbbreviation("DataElement")).isEqualTo("DE");
        assertThat(IdentifierStrategyConfigService.itemTypeAbbreviation("DataElementConcept")).isEqualTo("DEC");
        assertThat(IdentifierStrategyConfigService.itemTypeAbbreviation("ValueDomain")).isEqualTo("VD");
        assertThat(IdentifierStrategyConfigService.itemTypeAbbreviation(null)).isEmpty();
        assertThat(IdentifierStrategyConfigService.itemTypeAbbreviation("  ")).isEmpty();
    }

    @Test
    void shouldSubstituteNsAndTypeTokensInTemplate() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStrategyType("TEMPLATE");
        config.setConfigJson("{\"template\":\"{ns}.{type}.{seq:4}\",\"prefix\":\"\"}");
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);

        IdentifierGenerationStrategyRegistry registry = IdentifierGenerationStrategyRegistry.getInstance();
        registry.setGlobalDefault(config);

        Namespace ns = new Namespace();
        ns.setShorthandPrefix("org:");
        IdentifierStrategyConfigService.GenerationContext ctx =
                IdentifierStrategyConfigService.GenerationContext.ofSeq(1L);
        ctx.setItemType("DataElement");

        String identifier = service.generateIdentifier(ns, ctx);
        assertThat(identifier).isEqualTo("org:.DE.0001");
    }

    @Test
    void shouldSelfAllocateSequenceWhenTemplateHasSeqButContextDoesNot() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStrategyType("TEMPLATE");
        config.setConfigJson("{\"template\":\"{type}-{seq:4}\",\"prefix\":\"\",\"pad\":4}");
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);

        IdentifierGenerationStrategyRegistry registry = IdentifierGenerationStrategyRegistry.getInstance();
        registry.setGlobalDefault(config);

        IdentifierStrategyConfigService.GenerationContext ctx =
                IdentifierStrategyConfigService.GenerationContext.ofItemType("ValueDomain");

        String identifier = service.generateIdentifier(new Namespace(), ctx);
        assertThat(identifier).matches("^VD-\\d{4}$");
    }

    @Test
    void shouldSubstituteTypeTokenInSequencePrefix() {
        IdentifierStrategyConfig config = new IdentifierStrategyConfig();
        config.setStrategyType("SEQUENCE");
        config.setConfigJson("{\"start\":1,\"incrementBy\":1,\"pad\":6,\"prefix\":\"{type}-\"}");
        config.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);

        IdentifierGenerationStrategyRegistry registry = IdentifierGenerationStrategyRegistry.getInstance();
        registry.setGlobalDefault(config);

        IdentifierStrategyConfigService.GenerationContext ctx =
                IdentifierStrategyConfigService.GenerationContext.ofItemType("ValueDomain");

        String identifier = service.generateIdentifier(new Namespace(), ctx);
        assertThat(identifier).matches("^VD-\\d{6}$");
    }
}
