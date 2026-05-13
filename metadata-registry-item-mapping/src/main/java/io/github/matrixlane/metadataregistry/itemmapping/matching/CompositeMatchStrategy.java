package io.github.matrixlane.metadataregistry.itemmapping.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Composite matching strategy that orchestrates multiple sub-strategies.
 *
 * <p>Execution order:
 * <ol>
 *   <li><b>Exact identifier match</b> — highest priority. If an exact match is
 *       found, it is returned as the top candidate with score 1.00.</li>
 *   <li><b>Name similarity match</b> — applied to all candidates not already
 *       matched by identifier.</li>
 *   <li><b>Deduplication</b> — when the same local item is matched by multiple
 *       strategies, the highest confidence score is kept.</li>
 * </ol>
 *
 * <p>Returns up to {@code maxCandidates} candidates sorted by confidence descending.
 */
public class CompositeMatchStrategy implements MatchStrategy {

    private final List<MatchStrategy> strategies;
    private final MatchConfiguration configuration;

    /**
     * Creates a composite strategy with the default sub-strategies:
     * {@link ExactIdentifierMatchStrategy} then {@link NameSimilarityMatchStrategy}.
     *
     * @param configuration the matching configuration
     */
    public CompositeMatchStrategy(MatchConfiguration configuration) {
        this.configuration = configuration;
        this.strategies = List.of(
                new ExactIdentifierMatchStrategy(),
                new NameSimilarityMatchStrategy(configuration)
        );
    }

    /**
     * Creates a composite strategy with custom sub-strategies.
     *
     * @param strategies a list of sub-strategies to execute in order
     * @param configuration the matching configuration
     */
    public CompositeMatchStrategy(List<MatchStrategy> strategies, MatchConfiguration configuration) {
        this.strategies = List.copyOf(strategies);
        this.configuration = configuration;
    }

    @Override
    public List<MatchCandidate> match(InteropElement source, List<LocalItemInfo> candidates) {
        if (source == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        // Collect results from all sub-strategies, deduplicated by itemId
        Map<UUID, MatchCandidate> bestPerItem = new HashMap<>();

        for (MatchStrategy strategy : strategies) {
            List<MatchCandidate> subResults = strategy.match(source, candidates);
            for (MatchCandidate candidate : subResults) {
                bestPerItem.merge(
                        candidate.localItemId(),
                        candidate,
                        (existing, incoming) -> incoming.confidenceScore() > existing.confidenceScore()
                                ? incoming
                                : existing
                );
            }
        }

        if (bestPerItem.isEmpty()) {
            return List.of();
        }

        // Sort by confidence descending, take top N
        int maxCandidates = configuration.getMaxCandidatesPerElement();
        return bestPerItem.values().stream()
                .sorted(Comparator.comparingDouble(MatchCandidate::confidenceScore).reversed()
                        .thenComparing(MatchCandidate::strategy) // EXACT_IDENTIFIER before NAME_SIMILARITY
                        .thenComparing(MatchCandidate::localItemId))
                .limit(maxCandidates)
                .toList();
    }

    @Override
    public MatchStrategyType getType() {
        return MatchStrategyType.COMPOSITE;
    }

    /**
     * Returns the list of sub-strategies.
     */
    public List<MatchStrategy> getStrategies() {
        return strategies;
    }
}
