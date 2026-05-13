package io.github.matrixlane.metadataregistry.itemmapping.matching;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Match suggestion for a single interop data element, containing up to 3 candidates.
 *
 * @param interopElementId the UUID of the interop data element being matched
 * @param topCandidates    up to 3 candidate results, sorted by confidence descending
 */
public record MatchSuggestion(
        UUID interopElementId,
        List<MatchCandidate> topCandidates
) {

    /**
     * Creates a MatchSuggestion, defensively copying the candidate list.
     */
    public MatchSuggestion {
        topCandidates = topCandidates == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(topCandidates));
    }

    /**
     * Returns the best candidate (highest confidence), or {@code null} if no candidates.
     */
    public MatchCandidate bestCandidate() {
        return topCandidates.isEmpty() ? null : topCandidates.get(0);
    }
}
