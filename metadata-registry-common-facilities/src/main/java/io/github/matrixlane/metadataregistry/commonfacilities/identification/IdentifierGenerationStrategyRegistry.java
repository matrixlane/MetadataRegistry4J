package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry for identifier generation strategies.
 *
 * <p>Provides Namespace-based routing with priority:
 * <ol>
 *   <li>Namespace-specific strategy (highest priority)</li>
 *   <li>Global default strategy (fallback)</li>
 * </ol>
 *
 * <p>Supports runtime registration of strategies.
 * Built-in strategies (UUID, URN, SEQUENCE, TEMPLATE) are registered by default.
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
     * Global default strategy config (single fallback for all namespaces).
     */
    private volatile IdentifierStrategyConfig globalDefault;

    /**
     * Namespace-specific strategy configs.
     * Key: namespace.getId()
     */
    private final Map<UUID, IdentifierStrategyConfig> namespaceConfigs = new ConcurrentHashMap<>();

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
     * Resolves the effective strategy for the given namespace.
     *
     * <p>Resolution order:
     * 1. Namespace-specific config → 2. Global default config → 3. UUID strategy
     *
     * @param namespace the namespace context (may be null for global resolution)
     * @return the resolved IdentifierGenerationStrategy (never null; falls back to UUID)
     */
    public IdentifierGenerationStrategy resolve(Namespace namespace) {
        // Try namespace-specific config
        if (namespace != null && namespace.getId() != null) {
            IdentifierStrategyConfig nsConfig = namespaceConfigs.get(namespace.getId());
            if (nsConfig != null && nsConfig.isEffective()) {
                return getStrategyInstance(nsConfig.getStrategyType());
            }
        }

        // Try global default config
        if (globalDefault != null && globalDefault.isEffective()) {
            return getStrategyInstance(globalDefault.getStrategyType());
        }

        // Ultimate fallback: UUID
        System.out.println("[IdentifierGenerationStrategyRegistry] No config found for namespace="
                + (namespace != null ? namespace.getId() : "null")
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
     * Sets the global default strategy config.
     * Called by the service layer when loading configs from DB.
     */
    public void setGlobalDefault(IdentifierStrategyConfig config) {
        this.globalDefault = config;
    }

    /**
     * Sets a namespace-specific strategy config.
     */
    public void setNamespaceConfig(UUID namespaceId, IdentifierStrategyConfig config) {
        if (config != null) {
            namespaceConfigs.put(namespaceId, config);
        } else {
            namespaceConfigs.remove(namespaceId);
        }
    }

    /**
     * Clears all cached configs (useful for refreshing from DB).
     */
    public void clearCache() {
        globalDefault = null;
        namespaceConfigs.clear();
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
     * Resolves the global default strategy config.
     */
    public IdentifierStrategyConfig resolveGlobalDefault() {
        return globalDefault;
    }

    /**
     * Resolves the namespace-specific strategy config.
     */
    public IdentifierStrategyConfig resolveNamespaceConfig(UUID namespaceId) {
        return namespaceConfigs.get(namespaceId);
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
}
