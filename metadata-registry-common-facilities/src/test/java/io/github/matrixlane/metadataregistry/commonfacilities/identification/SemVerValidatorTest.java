package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemVerValidatorTest {

    @ParameterizedTest
    @CsvSource({
            "1.0.0, true",
            "0.0.0, true",
            "10.20.30, true",
            "1.0.0-alpha, false",
            "1.0, false",
            "1, false",
            "v1.0.0, false",
            "1.0.0.0, false",
            "01.02.03, false",
            "'', false",
            "' ', false"
    })
    void shouldValidateSemver(String version, boolean expected) {
        assertThat(SemVerValidator.isValid(version)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0.0", "0.0.0", "10.20.30"})
    void validate_shouldPassForValidVersions(String version) {
        SemVerValidator.validate(version);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "v1.0.0", "", " "})
    void validate_shouldThrowForInvalidVersions(String version) {
        assertThatThrownBy(() -> SemVerValidator.validate(version))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid semantic version");
    }
}
