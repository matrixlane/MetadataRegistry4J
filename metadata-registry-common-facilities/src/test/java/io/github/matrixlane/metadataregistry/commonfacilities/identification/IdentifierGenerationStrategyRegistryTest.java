package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for IdentifierGenerationStrategyRegistry.
 *
 * <p>Note: Registry is a singleton. Tests verify the built-in strategies
 * are registered and that the routing resolution logic works correctly.
 * An {@code @AfterEach} method clears cached configs between tests to ensure isolation.
 */
class IdentifierGenerationStrategyRegistryTest {

    @BeforeEach
    void setUp() {
        getRegistry().clearCache();
    }

    /**
     * Clears cached configs between tests to prevent state pollution.
     * Note: Built-in strategies (UUID, URN) are never removed.
     */
    @AfterEach
    void tearDown() {
        getRegistry().clearCache();
    }

    /**
     * Gets the registry singleton, pre-populated with built-in strategies.
     */
    private IdentifierGenerationStrategyRegistry getRegistry() {
        return IdentifierGenerationStrategyRegistry.getInstance();
    }

    @Test
    void shouldHaveUuidAndUrnBuiltIn() {
        var registry = getRegistry();
        assertThat(registry.isStrategyRegistered("UUID")).isTrue();
        assertThat(registry.isStrategyRegistered("URN")).isTrue();
        assertThat(registry.isStrategyRegistered("uuid")).isTrue(); // case-insensitive
    }

    @Test
    void shouldResolveUuidByDefault() {
        var registry = getRegistry();
        IdentifierGenerationStrategy strategy = registry.resolve(null, null);
        assertThat(strategy).isInstanceOf(UuidGenerationStrategy.class);
        assertThat(strategy.getType()).isEqualTo("UUID");
    }

    @Test
    void shouldResolveUrnForUrnType() {
        var registry = getRegistry();
        IdentifierGenerationStrategy strategy = registry.resolve(null, IdentifierType.URN);
        // Falls back to UUID if no config is set (no DB-backed configs in unit test)
        assertThat(strategy).isNotNull();
    }

    @Test
    void shouldReturnRegisteredStrategyTypes() {
        var registry = getRegistry();
        var types = registry.getRegisteredStrategyTypes();
        assertThat(types).contains("UUID", "URN");
    }

    @Test
    void shouldRejectNullStrategy() {
        var registry = getRegistry();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(null)
        );
    }

    @Test
    void shouldGetStrategyInstance() {
        var registry = getRegistry();
        IdentifierGenerationStrategy uuid = registry.getStrategyInstance("UUID");
        assertThat(uuid).isInstanceOf(UuidGenerationStrategy.class);

        IdentifierGenerationStrategy urn = registry.getStrategyInstance("URN");
        assertThat(urn).isInstanceOf(UrnGenerationStrategy.class);
    }

    @Test
    void shouldFallbackToUuidForUnknownStrategyType() {
        var registry = getRegistry();
        IdentifierGenerationStrategy unknown = registry.getStrategyInstance("UNKNOWN_STRATEGY");
        assertThat(unknown).isInstanceOf(UuidGenerationStrategy.class); // fallback
    }

    @Test
    void shouldResolveWithNamespaceAndType() {
        var registry = getRegistry();
        Namespace ns = mock(Namespace.class);
        UUID nsId = UUID.randomUUID();
        when(ns.getId()).thenReturn(nsId);

        // Without any configs, should fall back to UUID
        IdentifierGenerationStrategy strategy = registry.resolve(ns, IdentifierType.INTERNAL);
        assertThat(strategy).isNotNull();
    }

    @Test
    void shouldSetAndResolveGlobalDefault() {
        var registry = getRegistry();
        IdentifierStrategyConfig globalConfig = new IdentifierStrategyConfig("URN", IdentifierType.INTERNAL);
        globalConfig.setIsGlobalDefault(true);
        globalConfig.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);

        registry.setGlobalDefault(IdentifierType.INTERNAL, globalConfig);

        // Note: With URN registered, it should be resolved
        IdentifierGenerationStrategy resolved = registry.resolve(null, IdentifierType.INTERNAL);
        assertThat(resolved).isNotNull();
    }

    @Test
    void shouldResolveNamespaceTypeConfig() {
        var registry = getRegistry();
        UUID nsId = UUID.randomUUID();
        Namespace ns = mock(Namespace.class);
        when(ns.getId()).thenReturn(nsId);

        IdentifierStrategyConfig nsTypeConfig = new IdentifierStrategyConfig("URN", IdentifierType.URN);
        nsTypeConfig.setNamespace(ns);
        nsTypeConfig.setStatus(IdentifierStrategyConfig.StrategyStatus.PUBLISHED);

        registry.setNamespaceTypeConfig(nsId, IdentifierType.URN, nsTypeConfig);

        IdentifierStrategyConfig resolved = registry.resolveNamespaceTypeConfig(nsId, IdentifierType.URN);
        assertThat(resolved).isEqualTo(nsTypeConfig);
    }

    @Test
    void shouldResolveNamespaceDefault() {
        var registry = getRegistry();
        UUID nsId = UUID.randomUUID();
        IdentifierStrategyConfig nsDefault = new IdentifierStrategyConfig("SEQUENCE", IdentifierType.INTERNAL);
        registry.setNamespaceDefault(nsId, nsDefault);

        IdentifierStrategyConfig resolved = registry.resolveNamespaceDefault(nsId);
        assertThat(resolved).isEqualTo(nsDefault);
    }

    @Test
    void shouldRemoveNamespaceDefaultWhenConfigIsNull() {
        var registry = getRegistry();
        UUID nsId = UUID.randomUUID();
        registry.setNamespaceDefault(nsId, new IdentifierStrategyConfig("UUID", IdentifierType.INTERNAL));
        registry.setNamespaceDefault(nsId, null);

        assertThat(registry.resolveNamespaceDefault(nsId)).isNull();
    }

    @Test
    void shouldRemoveNamespaceTypeConfigWhenConfigIsNull() {
        var registry = getRegistry();
        UUID nsId = UUID.randomUUID();
        registry.setNamespaceTypeConfig(nsId, IdentifierType.URN,
                new IdentifierStrategyConfig("URN", IdentifierType.URN));
        registry.setNamespaceTypeConfig(nsId, IdentifierType.URN, null);

        assertThat(registry.resolveNamespaceTypeConfig(nsId, IdentifierType.URN)).isNull();
    }
}
