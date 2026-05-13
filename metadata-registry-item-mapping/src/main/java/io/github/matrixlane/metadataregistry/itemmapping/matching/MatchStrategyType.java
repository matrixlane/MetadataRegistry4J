package io.github.matrixlane.metadataregistry.itemmapping.matching;

/**
 * Strategy types for matching algorithms.
 *
 * <p>Each type corresponds to a specific matching strategy implementation.
 * The order of enum constants reflects the priority (highest first) used
 * in the composite strategy.
 */
public enum MatchStrategyType {

    /**
     * Exact match on interop data element identifier vs local ScopedIdentifier.
     * Highest priority. When a match is found, it overrides name-based matches.
     */
    EXACT_IDENTIFIER,

    /**
     * Name similarity match using Levenshtein edit distance + Jaccard (2-gram)
     * with configurable weights.
     */
    NAME_SIMILARITY,

    /**
     * Composite strategy that runs EXACT_IDENTIFIER first, then NAME_SIMILARITY,
     * deduplicates, and returns Top-3 candidates.
     */
    COMPOSITE,

    /**
     * Reserved for V1 — semantic matching using NLP/embeddings.
     */
    SEMANTIC
}
