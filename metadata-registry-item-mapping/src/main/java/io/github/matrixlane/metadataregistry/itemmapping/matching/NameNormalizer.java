package io.github.matrixlane.metadataregistry.itemmapping.matching;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility for normalizing names and identifiers before matching.
 *
 * <p>Handles:
 * <ul>
 *   <li>Case folding for English text</li>
 *   <li>Unicode normalization (NFKC) for consistent character representation</li>
 *   <li>Special character handling: spaces, hyphens, parentheses, slashes</li>
 *   <li>Chinese/English mixed text</li>
 * </ul>
 */
public final class NameNormalizer {

    private static final Pattern SPECIAL_CHARS = Pattern.compile("[\\s\\-()/.]+");
    private static final Pattern LEADING_TRAILING_WS = Pattern.compile("^\\s+|\\s+$");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s{2,}");

    private NameNormalizer() {
    }

    /**
     * Normalizes a name for similarity comparison.
     *
     * <p>Steps:
     * <ol>
     *   <li>Unicode NFKC normalization</li>
     *   <li>Trim whitespace</li>
     *   <li>Collapse multiple spaces into single space</li>
     *   <li>Lowercase (for ASCII/Latin text)</li>
     * </ol>
     *
     * @param input the raw name
     * @return the normalized name, or empty string if input is null
     */
    public static String normalizeName(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String result = Normalizer.normalize(input, Normalizer.Form.NFKC);
        result = LEADING_TRAILING_WS.matcher(result).replaceAll("");
        result = MULTIPLE_SPACES.matcher(result).replaceAll(" ");
        return result.toLowerCase(Locale.ROOT);
    }

    /**
     * Normalizes an identifier for exact matching.
     *
     * <p>More aggressive than {@link #normalizeName} — removes all special
     * characters (spaces, hyphens, parentheses, slashes, dots) so that
     * e.g. "DE01.01.001" and "DE01-01-001" are considered equal.
     *
     * @param input the raw identifier
     * @return the normalized identifier, or empty string if input is null
     */
    public static String normalizeIdentifier(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String result = Normalizer.normalize(input, Normalizer.Form.NFKC);
        result = SPECIAL_CHARS.matcher(result).replaceAll("");
        return result.toLowerCase(Locale.ROOT);
    }
}
