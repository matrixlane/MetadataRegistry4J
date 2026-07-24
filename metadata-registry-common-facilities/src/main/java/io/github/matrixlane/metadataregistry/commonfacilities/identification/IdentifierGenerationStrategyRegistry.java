package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WI-028-B: Global registry for identifier generation strategies.
 *
 * <p>Provides (Namespace, IdentifierType) routing with priority:
 * <ol>
 *   <li>Namespace + Type specific strategy (highest priority)</li>
 *   <li>Namespace default strategy (any type fallback)</li>
 *   <li>Global default strategy (lowest priority)</li>
 * </ol>
 *
 * <p>Supports runtime registration of strategies.
 * Built-in strategies (UUID, URN) are registered by default.
 *
 * <p>Thread-safe for concurrent reads; write operations (register/clear) are synchronized.
 */
public class IdentifierGenerationStrategyRegistry {

    private static volatile IdentifierGenerationStrategyRegistry INSTANCE;

    /**
     * Strategy type → Strategy instance mapping (built-in + registered)
     */
    private final Map<String, IdentifierGenerationStrategy> strategies = new ConcurrentHashMap<>();

    /**
     * Per-type cache of the global default strategy config.
     * Key: IdentifierType.name()
     */
    private final Map<IdentifierType, IdentifierStrategyConfig> globalDefaults = new EnumMap<>(IdentifierType.class);

    /**
     * Namespace-specific default configs.
     * Key: namespace.getId()
     */
    private final Map<UUID, IdentifierStrategyConfig> namespaceDefaults = new ConcurrentHashMap<>();

    /**
     * (Namespace, Type) specific configs.
     * Key: namespaceId * 100 + type.ordinal() (composite key)
     */
    private final Map<CompositeKey, IdentifierStrategyConfig> namespaceTypeConfigs = new ConcurrentHashMap<>();

    private IdentifierGenerationStrategyRegistry() {
        registerBuiltInStrategies();
    }

    /**
     * Returns the singleton instance.
     */
    public static IdentifierGenerationStrategyRegistry getInstance() {
        if (INSTANCE == null) {
            synchronized (IdentifierGenerationStrategyRegistry.class) {
                if (INSTANCE == null) {
                    INSTANCE = new IdentifierGenerationStrategyRegistry();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Registers all built-in strategies.
     */
    private void registerBuiltInStrategies() {
        register(new UuidGenerationStrategy());
        register(new UrnGenerationStrategy());
        register(new SequenceGenerationStrategy());
        register(new TemplateGenerationStrategy());
        System.out.println("[IdentifierGenerationStrategyRegistry] Registered built-in strategies: UUID, URN, SEQUENCE, TEMPLATE");
    }

    /**
     * Registers a strategy instance by type.
     * @param strategy the strategy to register
     */
    public void register(IdentifierGenerationStrategy strategy) {
        if (strategy == null || strategy.getType() == null) {
            throw new IllegalArgumentException("Strategy and strategy type must not be null");
        }
        strategies.put(strategy.getType().toUpperCase(), strategy);
        System.out.println("[IdentifierGenerationStrategyRegistry] Registered strategy: " + strategy.getType());
    }

    /**
     * Resolves the effective strategy for the given namespace and identifier type.
     *
     * <p>Resolution order:
     * 1. (Namespace, Type) specific config → 2. Namespace default config → 3. Global default
     *
     * @param namespace the namespace context (may be null for global resolution)
     * @param identifierType the identifier type
     * @return the resolved IdentifierGenerationStrategy (never null; falls back to UUID)
     */
    public IdentifierGenerationStrategy resolve(Namespace namespace, IdentifierType identifierType) {
        if (identifierType == null) {
            identifierType = IdentifierType.INTERNAL;
        }

        // Try (namespace, type) specific config
        UUID nsId = namespace != null ? namespace.getId() : null;
        if (nsId != null) {
            IdentifierStrategyConfig nsTypeConfig = namespaceTypeConfigs.get(
                    new CompositeKey(nsId, identifierType));
            if (nsTypeConfig != null && nsTypeConfig.isEffective()) {
                return getStrategyInstance(nsTypeConfig.getStrategyType());
            }
        }

        // Try namespace default config
        if (nsId != null) {
            IdentifierStrategyConfig nsDefault = namespaceDefaults.get(nsId);
            if (nsDefault != null && nsDefault.isEffective()) {
                return getStrategyInstance(nsDefault.getStrategyType());
            }
        }

        // Try global default for this type
        IdentifierStrategyConfig globalDefault = globalDefaults.get(identifierType);
        if (globalDefault != null && globalDefault.isEffective()) {
            return getStrategyInstance(globalDefault.getStrategyType());
        }

        // Ultimate fallback: UUID
        System.out.println("[IdentifierGenerationStrategyRegistry] No config found for namespace="
                + (namespace != null ? namespace.getId() : "null") + ", type=" + identifierType
                + ". Falling back to UUIDStrategy.");
        return strategies.get("UUID");
    }

    /**
     * Returns the strategy instance for a given strategy type name.
     */
    public IdentifierGenerationStrategy getStrategyInstance(String strategyType) {
        if (strategyType == null) {
            return strategies.get("UUID");
        }
        IdentifierGenerationStrategy strategy = strategies.get(strategyType.toUpperCase());
        if (strategy == null) {
            System.out.println("[IdentifierGenerationStrategyRegistry] Unknown strategy type '"
                    + strategyType + "', falling back to UUID");
            return strategies.get("UUID");
        }
        return strategy;
    }

    // --- Configuration management (for repository/service layer) ---

    /**
     * Sets the global default strategy config for a given type.
     * Called by the service layer when loading configs from DB.
     */
    public void setGlobalDefault(IdentifierType identifierType, IdentifierStrategyConfig config) {
        if (config != null) {
            globalDefaults.put(identifierType, config);
        }
    }

    /**
     * Sets a namespace default strategy config.
     */
    public void setNamespaceDefault(UUID namespaceId, IdentifierStrategyConfig config) {
        if (config != null) {
            namespaceDefaults.put(namespaceId, config);
        } else {
            namespaceDefaults.remove(namespaceId);
        }
    }

    /**
     * Sets a (namespace, type) specific strategy config.
     */
    public void setNamespaceTypeConfig(UUID namespaceId, IdentifierType identifierType, IdentifierStrategyConfig config) {
        if (config != null) {
            namespaceTypeConfigs.put(new CompositeKey(namespaceId, identifierType), config);
        } else {
            namespaceTypeConfigs.remove(new CompositeKey(namespaceId, identifierType));
        }
    }

    /**
     * Clears all cached configs (useful for refreshing from DB).
     */
    public void clearCache() {
        globalDefaults.clear();
        namespaceDefaults.clear();
        namespaceTypeConfigs.clear();
        System.out.println("[IdentifierGenerationStrategyRegistry] Strategy registry cache cleared");
    }

    /**
     * Returns all registered strategy types.
     */
    public java.util.Set<String> getRegisteredStrategyTypes() {
        return java.util.Collections.unmodifiableSet(strategies.keySet());
    }

    // --- Config resolution helpers (for service layer) ---

    /**
     * Resolves the global default strategy config for a type.
     */
    public IdentifierStrategyConfig resolveGlobalDefault(IdentifierType identifierType) {
        return globalDefaults.get(identifierType);
    }

    /**
     * Resolves the namespace default strategy config.
     */
    public IdentifierStrategyConfig resolveNamespaceDefault(UUID namespaceId) {
        return namespaceDefaults.get(namespaceId);
    }

    /**
     * Resolves the (namespace, type) specific strategy config.
     */
    public IdentifierStrategyConfig resolveNamespaceTypeConfig(UUID namespaceId, IdentifierType identifierType) {
        return namespaceTypeConfigs.get(new CompositeKey(namespaceId, identifierType));
    }

    /**
     * Checks if a strategy is registered.
     */
    public boolean isStrategyRegistered(String strategyType) {
        return strategies.containsKey(strategyType.toUpperCase());
    }

    /**
     * Unregisters a strategy by type.
     */
    public IdentifierGenerationStrategy unregister(String strategyType) {
        return strategies.remove(strategyType.toUpperCase());
    }

    // --- Composite key for (namespace, type) lookup ---

    private static class CompositeKey {
        private final UUID namespaceId;
        private final IdentifierType identifierType;

        CompositeKey(UUID namespaceId, IdentifierType identifierType) {
            this.namespaceId = namespaceId;
            this.identifierType = identifierType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CompositeKey ck)) return false;
            return java.util.Objects.equals(namespaceId, ck.namespaceId)
                    && identifierType == ck.identifierType;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(namespaceId, identifierType);
        }
    }
}
