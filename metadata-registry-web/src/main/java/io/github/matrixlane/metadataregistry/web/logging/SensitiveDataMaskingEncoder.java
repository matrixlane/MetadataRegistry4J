package io.github.matrixlane.metadataregistry.web.logging;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Logback encoder that masks sensitive data in log output.
 * Masks: password, secret, token, authorization, apiKey fields.
 *
 * <p><strong>Note on domain content:</strong> Because this encoder operates on the
 * final formatted message string, it may occasionally mask legitimate metadata
 * concept names (e.g., {@code "PasswordStrength"}). For structured request data
 * (query strings, headers), prefer {@link SensitiveDataMasker} at the logging
 * call site to avoid false positives.
 */
public class SensitiveDataMaskingEncoder extends PatternLayoutEncoder {

    private static final Pattern[] SENSITIVE_PATTERNS = {
            // JSON-style fields
            Pattern.compile("\"password\"\\s*:\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"secret\"\\s*:\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"token\"\\s*:\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"apiKey\"\\s*:\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"api_key\"\\s*:\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE),
            // HTTP Header style
            Pattern.compile("Authorization\\s*:\\s*Bearer\\s+\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Authorization\\s*=\\s*Bearer\\s+\\S+", Pattern.CASE_INSENSITIVE),
            // Key-Value style
            Pattern.compile("(?i)(password|secret|token|apiKey|api_key)\\s*=\\s*[^\\s&;]+")
    };

    private static final String[] MASKED_REPLACEMENTS = {
            "\"password\":\"***\"",
            "\"secret\":\"***\"",
            "\"token\":\"***\"",
            "\"apiKey\":\"***\"",
            "\"api_key\":\"***\"",
            "Authorization: Bearer ***",
            "Authorization=Bearer ***",
            "$1=***"
    };

    @Override
    public byte[] encode(ILoggingEvent event) {
        String formatted = layout.doLayout(event);
        String masked = maskSensitiveData(formatted);
        Charset charset = getCharset();
        return masked.getBytes(charset != null ? charset : StandardCharsets.UTF_8);
    }

    @Override
    public byte[] headerBytes() {
        return new byte[0];
    }

    @Override
    public byte[] footerBytes() {
        return new byte[0];
    }

    static String maskSensitiveData(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        for (int i = 0; i < SENSITIVE_PATTERNS.length; i++) {
            result = SENSITIVE_PATTERNS[i].matcher(result).replaceAll(MASKED_REPLACEMENTS[i]);
        }
        return result;
    }
}
