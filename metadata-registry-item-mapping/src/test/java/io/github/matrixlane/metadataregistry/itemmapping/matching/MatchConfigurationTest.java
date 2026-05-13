package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchConfigurationTest {

    @Test
    void constructor_shouldSetDefaults() {
        MatchConfiguration config = new MatchConfiguration();
        assertThat(config.getLevenshteinWeight()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.01));
        assertThat(config.getJaccardWeight()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.01));
        assertThat(config.getHighConfidenceThreshold()).isCloseTo(0.85, org.assertj.core.data.Offset.offset(0.01));
        assertThat(config.getMediumConfidenceThreshold()).isCloseTo(0.60, org.assertj.core.data.Offset.offset(0.01));
        assertThat(config.getLevenshteinNormalization()).isEqualTo("longer");
        assertThat(config.getMaxCandidatesPerElement()).isEqualTo(3);
    }

    @Test
    void copyConstructor_shouldCopyValues() {
        MatchConfiguration original = new MatchConfiguration();
        original.setLevenshteinWeight(0.5);
        original.setJaccardWeight(0.5);

        MatchConfiguration copy = new MatchConfiguration(original);
        assertThat(copy.getLevenshteinWeight()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
        assertThat(copy.getJaccardWeight()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void setLevenshteinWeight_shouldClampToRange() {
        MatchConfiguration config = new MatchConfiguration();
        config.setLevenshteinWeight(0.3);
        assertThat(config.getLevenshteinWeight()).isCloseTo(0.3, org.assertj.core.data.Offset.offset(0.01));

        assertThatThrownBy(() -> config.setLevenshteinWeight(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.setLevenshteinWeight(1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setJaccardWeight_shouldClampToRange() {
        MatchConfiguration config = new MatchConfiguration();
        config.setJaccardWeight(0.7);
        assertThat(config.getJaccardWeight()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.01));

        assertThatThrownBy(() -> config.setJaccardWeight(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setHighConfidenceThreshold_shouldValidateRange() {
        MatchConfiguration config = new MatchConfiguration();
        config.setHighConfidenceThreshold(0.90);
        assertThat(config.getHighConfidenceThreshold()).isCloseTo(0.90, org.assertj.core.data.Offset.offset(0.01));

        assertThatThrownBy(() -> config.setHighConfidenceThreshold(1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setLevenshteinNormalization_shouldValidate() {
        MatchConfiguration config = new MatchConfiguration();
        config.setLevenshteinNormalization("sum");
        assertThat(config.getLevenshteinNormalization()).isEqualTo("sum");

        assertThatThrownBy(() -> config.setLevenshteinNormalization("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setMaxCandidatesPerElement_shouldValidate() {
        MatchConfiguration config = new MatchConfiguration();
        config.setMaxCandidatesPerElement(5);
        assertThat(config.getMaxCandidatesPerElement()).isEqualTo(5);

        assertThatThrownBy(() -> config.setMaxCandidatesPerElement(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getWeightSum_shouldCalculateCorrectly() {
        MatchConfiguration config = new MatchConfiguration();
        // Default: 0.6 + 0.4 = 1.0
        assertThat(config.getWeightSum()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));

        config.setLevenshteinWeight(0.7);
        config.setJaccardWeight(0.5);
        // 0.7 + 0.5 = 1.2 (supports sum ≠ 1)
        assertThat(config.getWeightSum()).isCloseTo(1.2, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void getWeightSum_shouldHandleZeroWeights() {
        MatchConfiguration config = new MatchConfiguration();
        config.setLevenshteinWeight(0.0);
        config.setJaccardWeight(0.0);
        assertThat(config.getWeightSum()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
    }
}
