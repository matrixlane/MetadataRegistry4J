package io.github.matrixlane.metadataregistry.commonfacilities.identification;

/**
 * Service layer for identifier generation and strategy management.
 *
 * <p>This service provides the main entry point for generating scoped identifiers
 * with namespace-driven strategy selection. It coordinates:
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
    private SequenceProvider sequenceProvider = new SequenceProvider() {
        private long sequenceCounter = 0;

        @Override
        public synchronized long getNextValue(Namespace namespace,
                                              long start, int increment, int pad, String prefix) {
            if (sequenceCounter == 0) {
                sequenceCounter = start;
            } else {
                sequenceCounter += increment;
            }
            return sequenceCounter;
        }
    };

    public void setSequenceProvider(SequenceProvider sequenceProvider) {
        if (sequenceProvider != null) {
            this.sequenceProvider = sequenceProvider;
        }
    }

    public IdentifierStrategyConfigService() {
        this.registry = IdentifierGenerationStrategyRegistry.getInstance();
    }

    public IdentifierStrategyConfigService(IdentifierGenerationStrategyRegistry registry) {
        this.registry = registry;
    }

    /**
     * Generates a new identifier for the given namespace.
     *
     * @param namespace the namespace context (may be null for global defaults)
     * @param context optional generation context (for sequence/template strategies)
     * @return the generated identifier string
     */
    public String generateIdentifier(Namespace namespace, GenerationContext context) {
        IdentifierGenerationStrategy strategy = registry.resolve(namespace);
        String strategyType = strategy.getType();

        switch (strategyType) {
            case "UUID":
                return strategy.generate(namespace);

            case "URN":
                return strategy.generate(namespace);

            case "SEQUENCE":
                return generateSequenceIdentifier(namespace, context);

            case "TEMPLATE":
                return generateTemplateIdentifier(namespace, context);

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
                                                           GenerationContext context) {
        if (namespace == null) {
            System.out.println("[IdentifierStrategyConfigService] Namespace is null for SEQUENCE strategy, falling back to UUID");
            return registry.getStrategyInstance("UUID").generate(namespace);
        }

        // Get the sequence config from the strategy config
        IdentifierStrategyConfig config = resolveStrategyConfig(namespace);

        SequenceGenerationStrategy.Config seqConfig =
                SequenceGenerationStrategy.Config.fromJson(config != null ? config.getConfigJson() : null);

        long start = seqConfig != null ? seqConfig.getStart() : 1;
        int increment = seqConfig != null ? seqConfig.getIncrementBy() : 1;
        int pad = seqConfig != null ? seqConfig.getPad() : 6;
        String prefix = resolvePrefixTokens(
                seqConfig != null ? seqConfig.resolvePrefix() : "", namespace, context);

        long nextSeq = sequenceProvider.getNextValue(namespace, start, increment, pad, prefix);

        String paddedSeq = String.format("%0" + pad + "d", nextSeq);

        return prefix + paddedSeq;
    }

    /**
     * Generates a template-based identifier.
     */
    private String generateTemplateIdentifier(Namespace namespace,
                                               GenerationContext context) {
        IdentifierStrategyConfig config = resolveStrategyConfig(namespace);
        TemplateGenerationStrategy.Config tmplConfig =
                TemplateGenerationStrategy.Config.fromJson(config != null ? config.getConfigJson() : null);

        if (tmplConfig == null || tmplConfig.getTemplate() == null) {
            System.out.println("[IdentifierStrategyConfigService] No template config found, falling back to UUID");
            return registry.getStrategyInstance("UUID").generate(namespace);
        }

        long seq;
        if (context != null && context.getSequenceNumber() > 0) {
            seq = context.getSequenceNumber();
        } else if (tmplConfig.getTemplate().contains("{seq")) {
            // Allocate from the namespace sequence registry (pessimistic lock)
            seq = sequenceProvider.getNextValue(namespace, 1, 1, tmplConfig.getPad(), "");
        } else {
            seq = 0;
        }

        TemplateGenerationStrategy.TemplateContext tmplCtx =
                new TemplateGenerationStrategy.TemplateContext(seq, tmplConfig.getPrefix());
        tmplCtx.setNamespacePrefix(namespace != null && namespace.getShorthandPrefix() != null
                ? namespace.getShorthandPrefix() : "");
        tmplCtx.setItemType(itemTypeAbbreviation(context != null ? context.getItemType() : null));

        return TemplateGenerationStrategy.generateFromTemplate(tmplConfig.getTemplate(), tmplCtx);
    }

    /**
     * Substitutes {ns} (namespace shorthand prefix) and {type} (item type
     * abbreviation) tokens in a configured prefix string.
     */
    private String resolvePrefixTokens(String prefix, Namespace namespace, GenerationContext context) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        String resolved = prefix.replace("{ns}",
                namespace != null && namespace.getShorthandPrefix() != null
                        ? namespace.getShorthandPrefix() : "");
        return resolved.replace("{type}",
                itemTypeAbbreviation(context != null ? context.getItemType() : null));
    }

    /**
     * Derives an item type abbreviation from a class simple name by taking its
     * uppercase letters: DataElement → DE, DataElementConcept → DEC,
     * ValueDomain → VD. Returns empty string for null/blank input.
     */
    static String itemTypeAbbreviation(String classSimpleName) {
        if (classSimpleName == null || classSimpleName.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : classSimpleName.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Resolves the effective strategy config for the given namespace.
     */
    private IdentifierStrategyConfig resolveStrategyConfig(Namespace namespace) {
        if (namespace != null && namespace.getId() != null) {
            IdentifierStrategyConfig nsConfig = registry.resolveNamespaceConfig(namespace.getId());
            if (nsConfig != null) return nsConfig;
        }
        return registry.resolveGlobalDefault();
    }

    /**
     * Context for identifier generation.
     */
    public static class GenerationContext {
        private long sequenceNumber;
        private String prefix;
        private String itemType;

        public long getSequenceNumber() { return sequenceNumber; }
        public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }

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

        public static GenerationContext ofItemType(String itemType) {
            GenerationContext ctx = new GenerationContext();
            ctx.itemType = itemType;
            return ctx;
        }
    }
}
