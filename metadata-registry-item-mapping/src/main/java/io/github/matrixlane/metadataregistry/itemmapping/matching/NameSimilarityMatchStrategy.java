package io.github.matrixlane.metadataregistry.itemmapping.matching;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Name similarity matching strategy.
 *
 * <p>Computes similarity between the interop data element's name and local items'
 * names using a weighted combination of normalized Levenshtein edit distance and
 * Jaccard coefficient (character 2-grams).
 *
 * <p>Combination formula (configurable weights):
 * <pre>
 * score = (levenshteinWeight * normalizedLevenshtein + jaccardWeight * jaccard)
 *         / (levenshteinWeight + jaccardWeight)
 * </pre>
 * When sum of weights ≠ 1.0, the score is normalized by dividing by the sum
 * so the final score is always in [0.0, 1.0].
 */
public class NameSimilarityMatchStrategy implements MatchStrategy {

    private final MatchConfiguration configuration;

    /**
     * @param configuration the configuration containing weights and normalization settings
     */
    public NameSimilarityMatchStrategy(MatchConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public List<MatchCandidate> match(InteropElement source, List<LocalItemInfo> candidates) {
        if (source == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        String sourceName = NameNormalizer.normalizeName(source.getName());
        if (sourceName.isEmpty()) {
            return List.of();
        }

        double lWeight = configuration.getLevenshteinWeight();
        double jWeight = configuration.getJaccardWeight();
        double weightSum = configuration.getWeightSum();
        String norm = configuration.getLevenshteinNormalization();

        List<MatchCandidate> results = new ArrayList<>();

        for (LocalItemInfo candidate : candidates) {
            String candidateName = NameNormalizer.normalizeName(candidate.getName());
            if (candidateName.isEmpty()) {
                continue;
            }

            double levSim = SimilarityCalculator.levenshteinSimilarity(sourceName, candidateName, norm);
            double jacSim = SimilarityCalculator.jaccardSimilarity(sourceName, candidateName);

            // Compute weighted score, normalize by weight sum
            double rawScore = (lWeight * levSim + jWeight * jacSim);
            double score = (weightSum > 0.0) ? rawScore / weightSum : 0.0;

            // Clamp to [0.0, 1.0]
            score = Math.max(0.0, Math.min(1.0, score));

            // Round to 2 decimal places using BigDecimal to avoid floating-point artifacts
            score = BigDecimal.valueOf(score)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();

            // Skip noise-level matches
            if (score < 0.20) {
                continue;
            }

            String reason = String.format(
                    "NAME_SIMILARITY: Levenshtein=%.2f, Jaccard=%.2f, combined=%.2f " +
                    "(weights: lev=%.1f, jac=%.1f, norm=%s)",
                    levSim, jacSim, score, lWeight, jWeight, norm
            );

            results.add(new MatchCandidate(
                    candidate.getItemId(),
                    score,
                    MatchStrategyType.NAME_SIMILARITY,
                    reason
            ));
        }

        // Sort by confidence descending
        results.sort(null);
        return results;
    }

    @Override
    public MatchStrategyType getType() {
        return MatchStrategyType.NAME_SIMILARITY;
    }
}
