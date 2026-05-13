package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchConfigurationPropertiesTest {

    @Test
    void defaultValues_shouldMatchDefaults() {
        MatchConfigurationProperties props = new MatchConfigurationProperties();

        assertThat(props.getLevenshteinWeight()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.001));
        assertThat(props.getJaccardWeight()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.001));
        assertThat(props.getHighConfidenceThreshold()).isCloseTo(0.85, org.assertj.core.data.Offset.offset(0.001));
        assertThat(props.getMediumConfidenceThreshold()).isCloseTo(0.60, org.assertj.core.data.Offset.offset(0.001));
        assertThat(props.getLevenshteinNormalization()).isEqualTo("longer");
        assertThat(props.getMaxCandidatesPerElement()).isEqualTo(3);
    }

    @Test
    void setters_shouldUpdateValues() {
        MatchConfigurationProperties props = new MatchConfigurationProperties();

        props.setLevenshteinWeight(0.7);
        props.setJaccardWeight(0.3);
        props.setHighConfidenceThreshold(0.80);
        props.setMediumConfidenceThreshold(0.50);
        props.setLevenshteinNormalization("sum");
        props.setMaxCandidatesPerElement(5);

        assertThat(props.getLevenshteinWeight()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.001));
        assertThat(props.getJaccardWeight()).isCloseTo(0.3, org.assertj.core.data.Offset.offset(0.001));
        assertThat(props.getHighConfidenceThreshold()).isCloseTo(0.80, org.assertj.core.data.Offset.offset(0.001));
        assertThat(props.getMediumConfidenceThreshold()).isCloseTo(0.50, org.assertj.core.data.Offset.offset(0.001));
        assertThat(props.getLevenshteinNormalization()).isEqualTo("sum");
        assertThat(props.getMaxCandidatesPerElement()).isEqualTo(5);
    }

    @Test
    void toMatchConfiguration_shouldConvertCorrectly() {
        MatchConfigurationProperties props = new MatchConfigurationProperties();
        props.setLevenshteinWeight(0.7);
        props.setJaccardWeight(0.3);
        props.setHighConfidenceThreshold(0.80);
        props.setMediumConfidenceThreshold(0.50);
        props.setLevenshteinNormalization("sum");
        props.setMaxCandidatesPerElement(5);

        MatchConfiguration config = props.toMatchConfiguration();

        assertThat(config.getLevenshteinWeight()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.001));
        assertThat(config.getJaccardWeight()).isCloseTo(0.3, org.assertj.core.data.Offset.offset(0.001));
        assertThat(config.getHighConfidenceThreshold()).isCloseTo(0.80, org.assertj.core.data.Offset.offset(0.001));
        assertThat(config.getMediumConfidenceThreshold()).isCloseTo(0.50, org.assertj.core.data.Offset.offset(0.001));
        assertThat(config.getLevenshteinNormalization()).isEqualTo("sum");
        assertThat(config.getMaxCandidatesPerElement()).isEqualTo(5);
    }

    @Test
    void toMatchConfiguration_withDefaults_shouldProduceValidConfig() {
        MatchConfigurationProperties props = new MatchConfigurationProperties();
        MatchConfiguration config = props.toMatchConfiguration();

        assertThat(config).isNotNull();
        assertThat(config.getWeightSum()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
    }
}
