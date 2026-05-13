package io.github.matrixlane.metadataregistry.itemmapping.matching;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility for computing string similarity measures.
 *
 * <p>Provides Levenshtein edit distance (with configurable normalization)
 * and Jaccard coefficient based on character bigrams (2-grams).
 */
public final class SimilarityCalculator {

    private SimilarityCalculator() {
    }

    /**
     * Computes normalized Levenshtein similarity between two strings.
     *
     * <p>Returns a value in [0.0, 1.0] where 1.0 indicates identical strings.
     *
     * @param s1        the first string
     * @param s2        the second string
     * @param normalize the normalization approach: "longer" or "sum"
     * @return similarity score in [0.0, 1.0], or 1.0 if both strings are empty
     */
    public static double levenshteinSimilarity(String s1, String s2, String normalize) {
        String a = s1 != null ? s1 : "";
        String b = s2 != null ? s2 : "";

        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        int distance = levenshteinDistance(a, b);

        double denominator;
        if ("sum".equalsIgnoreCase(normalize)) {
            denominator = (double) a.length() + b.length();
        } else {
            // "longer" (default)
            denominator = Math.max(a.length(), b.length());
        }

        if (denominator == 0.0) {
            return 1.0;
        }
        return 1.0 - (distance / denominator);
    }

    /**
     * Computes the Levenshtein (edit) distance between two strings.
     *
     * <p>This is the minimum number of single-character edits (insertions,
     * deletions, or substitutions) required to transform one string into another.
     *
     * @param s1 the first string
     * @param s2 the second string
     * @return the edit distance
     */
    public static int levenshteinDistance(String s1, String s2) {
        String a = s1 != null ? s1 : "";
        String b = s2 != null ? s2 : "";

        int m = a.length();
        int n = b.length();

        // Optimization: if one string is empty, distance = length of the other
        if (m == 0) return n;
        if (n == 0) return m;

        // Use two rows to save memory
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = min(
                        prev[j] + 1,        // deletion
                        curr[j - 1] + 1,    // insertion
                        prev[j - 1] + cost   // substitution
                );
            }
            // Swap rows
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[n];
    }

    /**
     * Computes Jaccard similarity coefficient based on character bigrams (2-grams).
     *
     * <p>J(A, B) = |A ∩ B| / |A ∪ B|
     * Returns a value in [0.0, 1.0] where 1.0 indicates identical bigram sets.
     *
     * @param s1 the first string
     * @param s2 the second string
     * @return Jaccard coefficient in [0.0, 1.0], or 1.0 if both strings produce no bigrams
     */
    public static double jaccardSimilarity(String s1, String s2) {
        String a = s1 != null ? s1 : "";
        String b = s2 != null ? s2 : "";

        Set<String> bigramsA = generateBigrams(a);
        Set<String> bigramsB = generateBigrams(b);

        if (bigramsA.isEmpty() && bigramsB.isEmpty()) {
            return 1.0;
        }
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) {
            return 0.0;
        }

        // Intersection
        Set<String> intersection = new HashSet<>(bigramsA);
        intersection.retainAll(bigramsB);

        // Union
        Set<String> union = new HashSet<>(bigramsA);
        union.addAll(bigramsB);

        return (double) intersection.size() / union.size();
    }

    /**
     * Generates character bigrams (2-grams) from a string.
     *
     * <p>For a string of length n, produces n-1 bigrams.
     * For example, "hello" produces: "he", "el", "ll", "lo".
     * For Chinese text, each character is a separate unit, so
     * "患者姓名" produces: "患者", "者姓", "姓名".
     *
     * @param s the input string
     * @return set of bigrams, or empty set if string length < 2
     */
    static Set<String> generateBigrams(String s) {
        Set<String> bigrams = new HashSet<>();
        if (s == null || s.length() < 2) {
            return bigrams;
        }
        for (int i = 0; i < s.length() - 1; i++) {
            bigrams.add(s.substring(i, i + 2));
        }
        return bigrams;
    }

    private static int min(int a, int b, int c) {
        return Math.min(a, Math.min(b, c));
    }
}
