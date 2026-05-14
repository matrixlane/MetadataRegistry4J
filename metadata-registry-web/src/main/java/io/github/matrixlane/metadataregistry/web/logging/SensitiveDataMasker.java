package io.github.matrixlane.metadataregistry.web.logging;

import jakarta.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Structured sensitive-data masking utility for use at logging call sites.
 * Unlike {@link SensitiveDataMaskingEncoder}, which applies regex-based masking
 * to the final formatted log message, this class masks values in structured
 * strings (query parameters, headers) before they are logged, avoiding false
 * positives on domain model content (e.g., a metadata element named
 * {@code "PasswordStrength"}).
 *
 * @see ApiLoggingInterceptor
 * @see SensitiveDataMaskingEncoder
 */
public final class SensitiveDataMasker {

    private SensitiveDataMasker() {
        // utility class
    }

    /**
     * Masks sensitive query parameter values in a URL query string.
     * Matching keys (case-insensitive): password, secret, token, apiKey, api_key.
     *
     * @param queryString raw query string, e.g. {@code "user=admin&password=secret123"}
     * @return masked query string, e.g. {@code "user=admin&password=***"}
     */
    @Nullable
    public static String maskQueryString(@Nullable String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return queryString;
        }
        return QUERY_PARAM_PATTERN.matcher(queryString).replaceAll("$1=***");
    }

    /**
     * Masks the value portion of an HTTP Authorization header.
     *
     * @param authHeader raw Authorization header value
     * @return masked value, e.g. {@code "Bearer ***"}
     */
    @Nullable
    public static String maskAuthorizationHeader(@Nullable String authHeader) {
        if (authHeader == null || authHeader.isEmpty()) {
            return authHeader;
        }
        return AUTHORIZATION_PATTERN.matcher(authHeader).replaceAll("$1 ***");
    }

    // (?i) = case-insensitive; group 1 = key; group 2 = value
    private static final Pattern QUERY_PARAM_PATTERN =
            Pattern.compile("(?i)(password|secret|token|apiKey|api_key)=([^\u0026]*)");

    // Matches "Bearer <token>" or "Basic <credentials>" after optional whitespace
    private static final Pattern AUTHORIZATION_PATTERN =
            Pattern.compile("(?i)^(Bearer|Basic)\\s+\\S+");
}
