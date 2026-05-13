package io.github.matrixlane.metadataregistry.web.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskingEncoderTest {

    @Test
    void maskSensitiveData_shouldMaskPasswordJson() {
        String input = "User login with password: \"password\":\"secret123\"";
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(input);
        assertThat(result).isEqualTo("User login with password: \"password\":\"***\"");
    }

    @Test
    void maskSensitiveData_shouldMaskSecretJson() {
        String input = "Config contains \"secret\":\"my-api-secret\"";
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(input);
        assertThat(result).isEqualTo("Config contains \"secret\":\"***\"");
    }

    @Test
    void maskSensitiveData_shouldMaskTokenJson() {
        String input = "Auth header: \"token\":\"eyJhbGciOiJIUzI1NiJ9\"";
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(input);
        assertThat(result).isEqualTo("Auth header: \"token\":\"***\"");
    }

    @Test
    void maskSensitiveData_shouldMaskApiKeyJson() {
        String input = "Request with \"apiKey\":\"abc-def-123\"";
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(input);
        assertThat(result).isEqualTo("Request with \"apiKey\":\"***\"");
    }

    @Test
    void maskSensitiveData_shouldMaskApiKeyUnderscoreJson() {
        String input = "Request with \"api_key\":\"abc_def_123\"";
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(input);
        assertThat(result).isEqualTo("Request with \"api_key\":\"***\"");
    }

    @Test
    void maskSensitiveData_shouldMaskAuthorizationHeader() {
        String input = "Headers: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xyz";
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(input);
        assertThat(result).isEqualTo("Headers: Authorization: Bearer ***");
    }

    @Test
    void maskSensitiveData_shouldMaskAuthorizationEquals() {
        String input = "curl -H Authorization=Bearer eyJhbGciOiJIUzI1NiJ9";
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(input);
        assertThat(result).isEqualTo("curl -H Authorization=Bearer ***");
    }

    @Test
    void maskSensitiveData_shouldMaskKeyValueStyle() {
        String input = "query params: password=secret123&user=admin";
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(input);
        assertThat(result).isEqualTo("query params: password=***&user=admin");
    }

    @Test
    void maskSensitiveData_shouldHandleMultipleMatches() {
        String input = "password=secret123 token=abc456 apiKey=xyz789";
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(input);
        assertThat(result).isEqualTo("password=*** token=*** apiKey=***");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "normal log message", "username=john"})
    void maskSensitiveData_shouldNotAlterCleanInput(String input) {
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    void maskSensitiveData_shouldHandleNull() {
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(null);
        assertThat(result).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "\"password\":\"secret\", \"password\":\"***\"",
            "\"PASSWORD\":\"secret\", \"password\":\"***\"",
            "\"Token\":\"abc\", \"token\":\"***\""
    })
    void maskSensitiveData_shouldBeCaseInsensitive(String input, String expected) {
        String result = SensitiveDataMaskingEncoder.maskSensitiveData(input);
        assertThat(result).isEqualTo(expected);
    }
}
