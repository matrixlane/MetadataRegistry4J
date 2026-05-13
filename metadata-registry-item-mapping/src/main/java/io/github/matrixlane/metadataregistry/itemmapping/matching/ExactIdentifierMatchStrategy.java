package io.github.matrixlane.metadataregistry.itemmapping.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Exact identifier matching strategy.
 *
 * <p>Matches an interop data element's identifier against local items'
 * ScopedIdentifier values. The match is case-insensitive and ignores
 * special characters (spaces, hyphens, parentheses, slashes, dots).
 *
 * <p>When an exact match is found, the confidence score is 1.00 (perfect match).
 * If no exact match is found, returns an empty list (no candidates).
 *
 * <p>This strategy has the highest priority in the composite strategy.
 */
public class ExactIdentifierMatchStrategy implements MatchStrategy {

    @Override
    public List<MatchCandidate> match(InteropElement source, List<LocalItemInfo> candidates) {
        if (source == null || source.getIdentifier() == null || source.getIdentifier().isEmpty()) {
            return List.of();
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        String normalizedSource = NameNormalizer.normalizeIdentifier(source.getIdentifier());
        if (normalizedSource.isEmpty()) {
            return List.of();
        }

        List<MatchCandidate> results = new ArrayList<>();

        for (LocalItemInfo candidate : candidates) {
            for (String identifier : candidate.getIdentifiers()) {
                String normalizedCandidate = NameNormalizer.normalizeIdentifier(identifier);
                if (normalizedSource.equals(normalizedCandidate)) {
                    // Exact identifier match — score 1.00
                    results.add(new MatchCandidate(
                            candidate.getItemId(),
                            1.00,
                            MatchStrategyType.EXACT_IDENTIFIER,
                            formatReason(source.getIdentifier(), identifier)
                    ));
                    break; // Only one match per candidate (favor first match)
                }
            }
        }

        // Sort by confidence descending (all 1.00, so effectively by deterministic ordering)
        results.sort(null);
        return results;
    }

    @Override
    public MatchStrategyType getType() {
        return MatchStrategyType.EXACT_IDENTIFIER;
    }

    private static String formatReason(String sourceIdentifier, String matchedIdentifier) {
        return String.format("EXACT_IDENTIFIER: identifier '%s' exactly matches local ScopedIdentifier '%s' (score=1.00)",
                sourceIdentifier, matchedIdentifier);
    }
}
