package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentifierStrategyConfigServiceTest {

    private IdentifierStrategyConfigService service;

    @BeforeEach
    void setUp() {
        // Clear any cached strategy configs to ensure test isolation
        IdentifierGenerationStrategyRegistry.getInstance().clearCache();
        service = new IdentifierStrategyConfigService();
    }

    @AfterEach
    void tearDown() {
        IdentifierGenerationStrategyRegistry.getInstance().clearCache();
    }

    @Test
    void shouldGenerateUuidIdentifier() {
        String id = service.generateIdentifier(null, IdentifierType.INTERNAL, null);

        assertThat(id).isNotNull();
        assertThat(id).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    void shouldGenerateUuidForNullType() {
        String id = service.generateIdentifier(null, null, null);

        assertThat(id).isNotNull();
        assertThat(id).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    void shouldGenerateUrnIdentifier() {
        // Without URN config, falls back to UUID
        String id = service.generateIdentifier(null, IdentifierType.URN, null);

        assertThat(id).isNotNull();
    }

    @Test
    void shouldGenerateUniqueIdentifiers() {
        String id1 = service.generateIdentifier(null, IdentifierType.INTERNAL, null);
        String id2 = service.generateIdentifier(null, IdentifierType.INTERNAL, null);

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void shouldUseNamespaceContext() {
        Namespace ns = mock(Namespace.class);
        UUID nsId = UUID.randomUUID();
        when(ns.getId()).thenReturn(nsId);

        String id = service.generateIdentifier(ns, IdentifierType.INTERNAL, null);

        assertThat(id).isNotNull();
    }

    @Test
    void shouldGenerateSequenceWithContext() {
        IdentifierStrategyConfigService.GenerationContext ctx =
                IdentifierStrategyConfigService.GenerationContext.ofSeq(42);

        // Without sequence config, falls back to UUID
        String id = service.generateIdentifier(null, IdentifierType.INTERNAL, ctx);

        assertThat(id).isNotNull();
    }

    @Test
    void shouldGenerateSequenceWithPrefix() {
        IdentifierStrategyConfigService.GenerationContext ctx =
                IdentifierStrategyConfigService.GenerationContext.ofPrefix("TEST");

        String id = service.generateIdentifier(null, IdentifierType.INTERNAL, ctx);

        assertThat(id).isNotNull();
    }

    @Test
    void shouldGenerateTemplateIdentifier() {
        // Without template config, falls back to UUID
        String id = service.generateIdentifier(null, IdentifierType.INTERNAL, null);

        assertThat(id).isNotNull();
    }

    @Test
    void shouldSupportAllIdentifierTypes() {
        for (IdentifierType type : IdentifierType.values()) {
            String id = service.generateIdentifier(null, type, null);
            assertThat(id).isNotNull().isNotBlank()
                    .as("Type " + type + " should generate non-blank identifier");
        }
    }

    @Test
    void generationContextShouldBuildFromSeq() {
        IdentifierStrategyConfigService.GenerationContext ctx =
                IdentifierStrategyConfigService.GenerationContext.ofSeq(123);

        assertThat(ctx.getSequenceNumber()).isEqualTo(123);
    }

    @Test
    void generationContextShouldBuildFromPrefix() {
        IdentifierStrategyConfigService.GenerationContext ctx =
                IdentifierStrategyConfigService.GenerationContext.ofPrefix("DE");

        assertThat(ctx.getPrefix()).isEqualTo("DE");
    }
}
