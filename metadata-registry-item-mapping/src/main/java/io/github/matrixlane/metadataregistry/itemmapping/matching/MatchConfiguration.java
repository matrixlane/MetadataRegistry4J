package io.github.matrixlane.metadataregistry.itemmapping.matching;

/**
 * Configuration for the matching engine.
 *
 * <p>Contains weights, thresholds, and normalization settings.
 * All values are mutable so they can be loaded from configuration sources.
 * Instances are thread-safe after construction if not mutated.
 */
public class MatchConfiguration {

    // ── Name similarity weights ──────────────────────────────────────

    private double levenshteinWeight = 0.6;
    private double jaccardWeight = 0.4;

    // ── Confidence thresholds ────────────────────────────────────────

    private double highConfidenceThreshold = 0.85;
    private double mediumConfidenceThreshold = 0.60;

    // ── Normalization ────────────────────────────────────────────────

    /**
     * Levenshtein normalization approach.
     * <ul>
     *   <li>{@code "longer"} — denominator is {@code max(len1, len2)} (default)</li>
     *   <li>{@code "sum"} — denominator is {@code len1 + len2}</li>
     * </ul>
     */
    private String levenshteinNormalization = "longer";

    // ── Matching limits ──────────────────────────────────────────────

    private int maxCandidatesPerElement = 3;

    // ── Constructors ─────────────────────────────────────────────────

    public MatchConfiguration() {
    }

    /**
     * Copy constructor.
     */
    public MatchConfiguration(MatchConfiguration other) {
        this.levenshteinWeight = other.levenshteinWeight;
        this.jaccardWeight = other.jaccardWeight;
        this.highConfidenceThreshold = other.highConfidenceThreshold;
        this.mediumConfidenceThreshold = other.mediumConfidenceThreshold;
        this.levenshteinNormalization = other.levenshteinNormalization;
        this.maxCandidatesPerElement = other.maxCandidatesPerElement;
    }

    // ── Getters ──────────────────────────────────────────────────────

    public double getLevenshteinWeight() {
        return levenshteinWeight;
    }

    public double getJaccardWeight() {
        return jaccardWeight;
    }

    public double getHighConfidenceThreshold() {
        return highConfidenceThreshold;
    }

    public double getMediumConfidenceThreshold() {
        return mediumConfidenceThreshold;
    }

    public String getLevenshteinNormalization() {
        return levenshteinNormalization;
    }

    public int getMaxCandidatesPerElement() {
        return maxCandidatesPerElement;
    }

    // ── Setters ──────────────────────────────────────────────────────

    public void setLevenshteinWeight(double levenshteinWeight) {
        this.levenshteinWeight = clampWeight(levenshteinWeight);
    }

    public void setJaccardWeight(double jaccardWeight) {
        this.jaccardWeight = clampWeight(jaccardWeight);
    }

    public void setHighConfidenceThreshold(double highConfidenceThreshold) {
        this.highConfidenceThreshold = clampThreshold(highConfidenceThreshold);
    }

    public void setMediumConfidenceThreshold(double mediumConfidenceThreshold) {
        this.mediumConfidenceThreshold = clampThreshold(mediumConfidenceThreshold);
    }

    public void setLevenshteinNormalization(String levenshteinNormalization) {
        if (!"longer".equals(levenshteinNormalization) && !"sum".equals(levenshteinNormalization)) {
            throw new IllegalArgumentException(
                    "levenshteinNormalization must be 'longer' or 'sum', got: " + levenshteinNormalization);
        }
        this.levenshteinNormalization = levenshteinNormalization;
    }

    public void setMaxCandidatesPerElement(int maxCandidatesPerElement) {
        if (maxCandidatesPerElement < 1) {
            throw new IllegalArgumentException("maxCandidatesPerElement must be >= 1");
        }
        this.maxCandidatesPerElement = maxCandidatesPerElement;
    }

    // ── Validation helpers ───────────────────────────────────────────

    private static double clampWeight(double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Weight must be in range [0.0, 1.0]: " + value);
        }
        return value;
    }

    private static double clampThreshold(double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Threshold must be in range [0.0, 1.0]: " + value);
        }
        return value;
    }

    /**
     * Returns the effective sum of levenshteinWeight + jaccardWeight.
     * May be less than, equal to, or greater than 1.0.
     */
    public double getWeightSum() {
        return levenshteinWeight + jaccardWeight;
    }
}
