package io.github.matrixlane.metadataregistry.commonfacilities.identification;

/**
 * WI-028-B: Service layer for identifier generation and strategy management.
 *
 * <p>This service provides the main entry point for generating scoped identifiers
 * with type-driven strategy selection. It coordinates:
 * <ul>
 *   <li>Strategy resolution via IdentifierGenerationStrategyRegistry</li>
 *   <li>Sequence allocation for SequenceGenerationStrategy</li>
 *   <li>Template resolution for TemplateGenerationStrategy</li>
 * </ul>
 *
 * <p>Thread-safety: Uses synchronized blocks for sequence allocation to prevent
 * concurrent sequence number conflicts.
 *
 * <p>Note: This service uses regex-based JSON parsing to avoid Jackson dependency
 * in the common-facilities module. For full JSON support, use the web module's
 * Jackson-enabled service.
 */
public class IdentifierStrategyConfigService {

    private final IdentifierGenerationStrategyRegistry registry;

    public IdentifierStrategyConfigService() {
        this.registry = IdentifierGenerationStrategyRegistry.getInstance();
    }

    public IdentifierStrategyConfigService(IdentifierGenerationStrategyRegistry registry) {
        this.registry = registry;
    }

    /**
     * Generates a new identifier for the given namespace and type.
     *
     * @param namespace the namespace context (may be null for global defaults)
     * @param identifierType the type of identifier to generate
     * @param context optional generation context (for sequence/template strategies)
     * @return the generated identifier string
     */
    public String generateIdentifier(Namespace namespace, IdentifierType identifierType,
                                    GenerationContext context) {
        IdentifierGenerationStrategy strategy = registry.resolve(namespace, identifierType);
        String strategyType = strategy.getType();

        switch (strategyType) {
            case "UUID":
                return strategy.generate(namespace);

            case "URN":
                return strategy.generate(namespace);

            case "SEQUENCE":
                return generateSequenceIdentifier(namespace, identifierType, context);

            case "TEMPLATE":
                return generateTemplateIdentifier(namespace, identifierType, context);

            default:
                System.out.println("[IdentifierStrategyConfigService] Unknown strategy type '"
                        + strategyType + "', falling back to UUID");
                return registry.getStrategyInstance("UUID").generate(namespace);
        }
    }

    /**
     * Generates a sequence identifier with atomic increment.
     *
     * <p>Note: This is a simplified in-memory implementation.
     * In production, this should delegate to a SequenceRegistryService
     * that handles database-level locking.
     */
    private synchronized String generateSequenceIdentifier(Namespace namespace,
                                                           IdentifierType identifierType,
                                                           GenerationContext context) {
        // Get the sequence config from the strategy config
        IdentifierStrategyConfig config = resolveStrategyConfig(namespace, identifierType);

        SequenceGenerationStrategy.Config seqConfig =
                SequenceGenerationStrategy.Config.fromJson(config != null ? config.getConfigJson() : null);

        // Simple atomic counter for demo (replace with DB SELECT FOR UPDATE in production)
        long nextSeq = getNextSequenceValue(namespace, identifierType, seqConfig);

        String prefix = seqConfig != null ? seqConfig.resolvePrefix() : "";
        int pad = seqConfig != null ? seqConfig.getPad() : 6;
        String paddedSeq = String.format("%0" + pad + "d", nextSeq);

        return prefix + paddedSeq;
    }

    /**
     * Generates a template-based identifier.
     */
    private String generateTemplateIdentifier(Namespace namespace,
                                              IdentifierType identifierType,
                                              GenerationContext context) {
        IdentifierStrategyConfig config = resolveStrategyConfig(namespace, identifierType);
        TemplateGenerationStrategy.Config tmplConfig =
                TemplateGenerationStrategy.Config.fromJson(config != null ? config.getConfigJson() : null);

        if (tmplConfig == null || tmplConfig.getTemplate() == null) {
            System.out.println("[IdentifierStrategyConfigService] No template config found, falling back to UUID");
            return registry.getStrategyInstance("UUID").generate(namespace);
        }

        long seq = context != null ? context.getSequenceNumber() : 0;

        TemplateGenerationStrategy.TemplateContext tmplCtx =
                new TemplateGenerationStrategy.TemplateContext(seq, tmplConfig.getPrefix());

        return TemplateGenerationStrategy.generateFromTemplate(tmplConfig.getTemplate(), tmplCtx);
    }

    /**
     * Resolves the effective strategy config for (namespace, type).
     */
    private IdentifierStrategyConfig resolveStrategyConfig(Namespace namespace, IdentifierType identifierType) {
        if (namespace != null && namespace.getId() != null) {
            // Try namespace+type specific
            IdentifierStrategyConfig nsTypeConfig = registry.resolveNamespaceTypeConfig(
                    namespace.getId(), identifierType);
            if (nsTypeConfig != null) return nsTypeConfig;

            // Try namespace default
            IdentifierStrategyConfig nsDefault = registry.resolveNamespaceDefault(namespace.getId());
            if (nsDefault != null) return nsDefault;
        }

        // Try global default
        return registry.resolveGlobalDefault(identifierType);
    }

    // --- Simple in-memory sequence state (replace with DB-backed service) ---

    private long sequenceCounter = 0;

    private synchronized long getNextSequenceValue(Namespace namespace,
                                                  IdentifierType identifierType,
                                                  SequenceGenerationStrategy.Config config) {
        long start = config != null ? config.getStart() : 1;
        long increment = config != null ? config.getIncrementBy() : 1;

        if (sequenceCounter == 0) {
            sequenceCounter = start;
        } else {
            sequenceCounter += increment;
        }
        return sequenceCounter;
    }

    /**
     * Context for identifier generation.
     */
    public static class GenerationContext {
        private long sequenceNumber;
        private String prefix;

        public long getSequenceNumber() { return sequenceNumber; }
        public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }

        public static GenerationContext ofSeq(long seq) {
            GenerationContext ctx = new GenerationContext();
            ctx.sequenceNumber = seq;
            return ctx;
        }

        public static GenerationContext ofPrefix(String prefix) {
            GenerationContext ctx = new GenerationContext();
            ctx.prefix = prefix;
            return ctx;
        }
    }
}
