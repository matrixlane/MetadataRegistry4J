package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Core matching engine service.
 *
 * <p>Orchestrates the matching of interop data elements against local registry
 * items using configurable strategies. Supports both synchronous and asynchronous
 * (future-based) invocation.
 *
 * <p>MVP implementation is synchronous. Async interface is provided via
 * {@link CompletableFuture} for future upgrade to Spring {@code @Async}.
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Single element matching
 * MatchSuggestion result = engineService.matchOne(interopElement, localItems);
 *
 * // Batch matching
 * List<MatchSuggestion> results = engineService.matchAll(interopElements, localItems);
 *
 * // Async batch matching
 * CompletableFuture<List<MatchSuggestion>> future = engineService.matchAllAsync(..., ...);
 * }</pre>
 */
@Service
public class MatchingEngineService {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngineService.class);

    private final MatchConfiguration configuration;
    private final CompositeMatchStrategy compositeStrategy;
    private final NameIndexCache nameIndexCache;

    /**
     * Creates the service with the given configuration properties.
     *
     * @param properties the configuration properties
     */
    @Autowired
    public MatchingEngineService(MatchConfigurationProperties properties) {
        this.configuration = properties.toMatchConfiguration();
        this.compositeStrategy = new CompositeMatchStrategy(this.configuration);
        this.nameIndexCache = new NameIndexCache();
        log.info("MatchingEngineService initialized with config: levWeight={}, jacWeight={}, " +
                        "highThreshold={}, medThreshold={}, normalization={}",
                configuration.getLevenshteinWeight(),
                configuration.getJaccardWeight(),
                configuration.getHighConfidenceThreshold(),
                configuration.getMediumConfidenceThreshold(),
                configuration.getLevenshteinNormalization());
    }

    /**
     * Creates the service with an explicit configuration (for testing).
     *
     * @param configuration the matching configuration
     * @param nameIndexCache the name index cache
     */
    MatchingEngineService(MatchConfiguration configuration, NameIndexCache nameIndexCache) {
        this.configuration = configuration;
        this.compositeStrategy = new CompositeMatchStrategy(this.configuration);
        this.nameIndexCache = nameIndexCache;
    }

    // ── Index Management ───────────────────────────────────────────────

    /**
     * Builds or rebuilds the name index from local items.
     */
    public void buildIndex(List<LocalItemInfo> localItems) {
        nameIndexCache.buildIndex(localItems);
        log.info("NameIndexCache rebuilt with {} items, {} distinct names",
                nameIndexCache.itemCount(), nameIndexCache.nameCount());
    }

    /**
     * Returns the current name index cache.
     */
    public NameIndexCache getNameIndexCache() {
        return nameIndexCache;
    }

    /**
     * Returns the current configuration.
     */
    public MatchConfiguration getConfiguration() {
        return configuration;
    }

    // ── Synchronous Matching ───────────────────────────────────────────

    /**
     * Matches a single interop data element against local items.
     *
     * <p>Uses the name index for pre-filtering to improve performance.
     *
     * @param source     the interop data element to match
     * @param candidates the full list of local candidate items
     * @return a match suggestion with up to 3 candidates
     */
    public MatchSuggestion matchOne(InteropElement source, List<LocalItemInfo> candidates) {
        if (source == null) {
            return new MatchSuggestion(null, List.of());
        }

        // Use index pre-filtering when available
        List<LocalItemInfo> filtered;
        if (nameIndexCache.itemCount() > 0) {
            filtered = nameIndexCache.preFilter(source.getName(), candidates);
        } else {
            filtered = candidates;
        }

        if (filtered == null || filtered.isEmpty()) {
            return new MatchSuggestion(source.getId(), List.of());
        }

        List<MatchCandidate> results = compositeStrategy.match(source, filtered);
        return new MatchSuggestion(source.getId(), results);
    }

    /**
     * Matches multiple interop data elements against local items.
     *
     * <p>Synchronous batch matching. For large datasets (1000+ elements),
     * consider using {@link #matchAllAsync(List, List)}.
     *
     * @param sources    the list of interop data elements to match
     * @param candidates the list of local candidate items
     * @return list of match suggestions, one per source element
     */
    public List<MatchSuggestion> matchAll(List<InteropElement> sources, List<LocalItemInfo> candidates) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        if (candidates == null || candidates.isEmpty()) {
            // Return empty suggestions for each source
            return sources.stream()
                    .map(s -> new MatchSuggestion(s.getId(), List.of()))
                    .toList();
        }

        // Ensure index is built
        if (nameIndexCache.itemCount() == 0) {
            buildIndex(candidates);
        }

        List<MatchSuggestion> results = new ArrayList<>(sources.size());
        for (InteropElement source : sources) {
            results.add(matchOne(source, candidates));
        }

        log.info("matchAll completed: {} sources matched against {} candidates, {} results produced",
                sources.size(), candidates.size(), results.size());
        return results;
    }

    /**
     * Matches interop data elements filtered by category/dataset code.
     *
     * <p>Allows narrowing the candidate set to a specific dataset or category,
     * improving performance for domain-specific matching.
     *
     * @param sources     the interop data elements to match
     * @param candidates  the local candidate items
     * @param preFiltered if true, candidates are assumed to already be a filtered subset
     * @return list of match suggestions
     */
    public List<MatchSuggestion> matchAllFiltered(List<InteropElement> sources,
                                                   List<LocalItemInfo> candidates,
                                                   boolean preFiltered) {
        if (preFiltered && nameIndexCache.itemCount() == 0) {
            // When pre-filtered, rebuild cache for just these candidates
            buildIndex(candidates);
        }
        return matchAll(sources, candidates);
    }

    // ── Asynchronous Matching ──────────────────────────────────────────

    /**
     * Asynchronously matches multiple interop data elements.
     *
     * <p>MVP: Uses {@link CompletableFuture#supplyAsync} for background execution.
     * V1: Will use Spring {@code @Async} for thread pool management.
     *
     * @param sources    the interop data elements to match
     * @param candidates the local candidate items
     * @return a future that completes with the list of match suggestions
     */
    public CompletableFuture<List<MatchSuggestion>> matchAllAsync(
            List<InteropElement> sources, List<LocalItemInfo> candidates) {
        return CompletableFuture.supplyAsync(() -> matchAll(sources, candidates));
    }

    // ── Confidence Level Classification ─────────────────────────────────

    /**
     * Classifies a confidence score into a match level.
     *
     * @param score the confidence score in [0.0, 1.0]
     * @return the match level
     */
    public MatchLevel classifyConfidence(double score) {
        if (score >= configuration.getHighConfidenceThreshold()) {
            return MatchLevel.HIGH;
        } else if (score >= configuration.getMediumConfidenceThreshold()) {
            return MatchLevel.MEDIUM;
        } else {
            return MatchLevel.LOW;
        }
    }

    /**
     * Match confidence levels.
     */
    public enum MatchLevel {
        /**
         * High confidence (>0.85 by default). Suitable for auto-matching.
         */
        HIGH,
        /**
         * Medium confidence (0.60-0.85 by default). Requires manual confirmation.
         */
        MEDIUM,
        /**
         * Low confidence (<0.60 by default). No match.
         */
        LOW
    }
}
