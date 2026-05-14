package io.github.matrixlane.metadataregistry.web.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    // ── maskQueryString ──────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
            "user=admin&password=secret123, user=admin&password=***",
            "password=secret123&token=abc, password=***&token=***",
            "apiKey=my-key-123&user=x, apiKey=***&user=x",
            "api_key=my_key&secret=shh, api_key=***&secret=***",
            "q=test&page=1, q=test&page=1",
            "PASSWORD=Secret123, PASSWORD=***",
            "Token=Bearerxyz, Token=***",
            "password=, password=***"
    })
    void maskQueryString_shouldMaskSensitiveParams(String input, String expected) {
        assertThat(SensitiveDataMasker.maskQueryString(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void maskQueryString_shouldHandleNullAndEmpty(String input) {
        assertThat(SensitiveDataMasker.maskQueryString(input)).isEqualTo(input);
    }

    // ── maskAuthorizationHeader ──────────────────────────────────────

    @Test
    void maskAuthorizationHeader_shouldMaskBearerToken() {
        assertThat(SensitiveDataMasker.maskAuthorizationHeader("Bearer eyJhbGciOiJIUzI1NiJ9"))
                .isEqualTo("Bearer ***");
    }

    @Test
    void maskAuthorizationHeader_shouldMaskBasicAuth() {
        assertThat(SensitiveDataMasker.maskAuthorizationHeader("Basic YWRtaW46c2VjcmV0"))
                .isEqualTo("Basic ***");
    }

    @Test
    void maskAuthorizationHeader_shouldPreserveCase() {
        assertThat(SensitiveDataMasker.maskAuthorizationHeader("bearer token123"))
                .isEqualTo("bearer ***");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void maskAuthorizationHeader_shouldHandleNullAndEmpty(String input) {
        assertThat(SensitiveDataMasker.maskAuthorizationHeader(input)).isEqualTo(input);
    }

    @Test
    void maskAuthorizationHeader_shouldNotAlterMalformedHeader() {
        // No space after Bearer
        assertThat(SensitiveDataMasker.maskAuthorizationHeader("Bearertoken123"))
                .isEqualTo("Bearertoken123");
    }
}
