package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring configuration properties for the matching engine.
 *
 * <p>Loads from {@code application.properties} or {@code application.yml}
 * under the prefix {@code mdr.matching}.
 *
 * <p>Example application.yml:
 * <pre>
 * mdr:
 *   matching:
 *     levenshtein-weight: 0.6
 *     jaccard-weight: 0.4
 *     high-confidence-threshold: 0.85
 *     medium-confidence-threshold: 0.60
 *     levenshtein-normalization: longer
 *     max-candidates-per-element: 3
 * </pre>
 */
@ConfigurationProperties(prefix = "mdr.matching")
public class MatchConfigurationProperties {

    /**
     * Weight for Levenshtein similarity in the combined name similarity score.
     * Range: [0.0, 1.0]. Default: 0.6.
     */
    private double levenshteinWeight = 0.6;

    /**
     * Weight for Jaccard similarity in the combined name similarity score.
     * Range: [0.0, 1.0]. Default: 0.4.
     */
    private double jaccardWeight = 0.4;

    /**
     * Threshold above which a match is considered high confidence.
     * Range: [0.0, 1.0]. Default: 0.85.
     */
    private double highConfidenceThreshold = 0.85;

    /**
     * Threshold below which a match is considered low confidence.
     * Range: [0.0, 1.0]. Default: 0.60.
     */
    private double mediumConfidenceThreshold = 0.60;

    /**
     * Levenshtein normalization approach: "longer" or "sum".
     * Default: "longer".
     */
    private String levenshteinNormalization = "longer";

    /**
     * Maximum number of candidates to return per interop data element.
     * Default: 3.
     */
    private int maxCandidatesPerElement = 3;

    // ── Getters and setters ──────────────────────────────────────────

    public double getLevenshteinWeight() {
        return levenshteinWeight;
    }

    public void setLevenshteinWeight(double levenshteinWeight) {
        this.levenshteinWeight = levenshteinWeight;
    }

    public double getJaccardWeight() {
        return jaccardWeight;
    }

    public void setJaccardWeight(double jaccardWeight) {
        this.jaccardWeight = jaccardWeight;
    }

    public double getHighConfidenceThreshold() {
        return highConfidenceThreshold;
    }

    public void setHighConfidenceThreshold(double highConfidenceThreshold) {
        this.highConfidenceThreshold = highConfidenceThreshold;
    }

    public double getMediumConfidenceThreshold() {
        return mediumConfidenceThreshold;
    }

    public void setMediumConfidenceThreshold(double mediumConfidenceThreshold) {
        this.mediumConfidenceThreshold = mediumConfidenceThreshold;
    }

    public String getLevenshteinNormalization() {
        return levenshteinNormalization;
    }

    public void setLevenshteinNormalization(String levenshteinNormalization) {
        this.levenshteinNormalization = levenshteinNormalization;
    }

    public int getMaxCandidatesPerElement() {
        return maxCandidatesPerElement;
    }

    public void setMaxCandidatesPerElement(int maxCandidatesPerElement) {
        this.maxCandidatesPerElement = maxCandidatesPerElement;
    }

    /**
     * Converts to a {@link MatchConfiguration} instance.
     */
    public MatchConfiguration toMatchConfiguration() {
        MatchConfiguration config = new MatchConfiguration();
        config.setLevenshteinWeight(this.levenshteinWeight);
        config.setJaccardWeight(this.jaccardWeight);
        config.setHighConfidenceThreshold(this.highConfidenceThreshold);
        config.setMediumConfidenceThreshold(this.mediumConfidenceThreshold);
        config.setLevenshteinNormalization(this.levenshteinNormalization);
        config.setMaxCandidatesPerElement(this.maxCandidatesPerElement);
        return config;
    }
}
