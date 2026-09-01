package io.github.matrixlane.metadataregistry.ext.identification;

import java.util.regex.Pattern;

/**
 * Validates semantic-version strings.
 *
 * <p>MVP: format check only ({@code major.minor.patch}).
 * Business-semantics enforcement deferred to V1.
 */
public final class SemVerValidator {

    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    private SemVerValidator() {
    }

    /**
     * Returns {@code true} if the given string is a valid SemVer.
     */
    public static boolean isValid(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        return SEMVER_PATTERN.matcher(version).matches();
    }

    /**
     * Validates and throws {@link IllegalArgumentException} on failure.
     */
    public static void validate(String version) {
        if (!isValid(version)) {
            throw new IllegalArgumentException(
                    "Invalid semantic version: '" + version + "'. Expected format: major.minor.patch");
        }
    }
}
