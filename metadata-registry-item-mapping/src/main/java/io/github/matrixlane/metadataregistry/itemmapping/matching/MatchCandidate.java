package io.github.matrixlane.metadataregistry.itemmapping.matching;

import java.util.UUID;

/**
 * A single matching candidate result.
 *
 * @param localItemId    the UUID of the matched local Item
 * @param confidenceScore confidence score in range [0.0, 1.0], rounded to 2 decimal places
 * @param strategy       the strategy that produced this candidate
 * @param reason         human-readable reason string including strategy identifier and score breakdown
 */
public record MatchCandidate(
        UUID localItemId,
        double confidenceScore,
        MatchStrategyType strategy,
        String reason
) implements Comparable<MatchCandidate> {

    /**
     * Default descending order by confidenceScore, then by strategy priority,
     * then by localItemId for stable ordering.
     */
    @Override
    public int compareTo(MatchCandidate o) {
        int cmp = Double.compare(o.confidenceScore, this.confidenceScore);
        if (cmp != 0) return cmp;
        cmp = this.strategy.compareTo(o.strategy);
        if (cmp != 0) return cmp;
        return this.localItemId.compareTo(o.localItemId);
    }
}
