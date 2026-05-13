package io.github.matrixlane.metadataregistry.itemmapping.matching;

import java.util.List;

/**
 * Strategy interface for matching algorithms.
 *
 * <p>Each strategy implementation compares a single {@link InteropElement}
 * against a list of candidate {@link LocalItemInfo} instances and produces
 * a list of {@link MatchCandidate} results.
 *
 * <p>This follows the Strategy pattern to allow easy addition of new
 * matching algorithms (e.g. semantic matching in V1).
 */
public interface MatchStrategy {

    /**
     * Matches a single interop data element against the given candidate items.
     *
     * @param source     the interop data element to match
     * @param candidates the list of local items to match against
     * @return a list of match candidates, sorted by confidence descending,
     *         or an empty list if no matches are found
     */
    List<MatchCandidate> match(InteropElement source, List<LocalItemInfo> candidates);

    /**
     * Returns the type of this strategy.
     *
     * @return the strategy type identifier
     */
    MatchStrategyType getType();
}
