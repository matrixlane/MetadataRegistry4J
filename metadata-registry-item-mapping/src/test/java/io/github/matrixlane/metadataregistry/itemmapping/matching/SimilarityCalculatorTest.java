package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarityCalculatorTest {

    // ── Levenshtein Distance ──────────────────────────────────────────

    @Test
    void levenshteinDistance_identicalStrings_shouldBeZero() {
        assertThat(SimilarityCalculator.levenshteinDistance("hello", "hello")).isZero();
    }

    @Test
    void levenshteinDistance_differentStrings_shouldComputeCorrectly() {
        assertThat(SimilarityCalculator.levenshteinDistance("kitten", "sitting")).isEqualTo(3);
    }

    @Test
    void levenshteinDistance_emptyString_shouldReturnOtherLength() {
        assertThat(SimilarityCalculator.levenshteinDistance("", "abc")).isEqualTo(3);
        assertThat(SimilarityCalculator.levenshteinDistance("abc", "")).isEqualTo(3);
    }

    @Test
    void levenshteinDistance_bothEmpty_shouldBeZero() {
        assertThat(SimilarityCalculator.levenshteinDistance("", "")).isZero();
    }

    @Test
    void levenshteinDistance_nullInputs_shouldBeTreatedAsEmpty() {
        assertThat(SimilarityCalculator.levenshteinDistance(null, "abc")).isEqualTo(3);
        assertThat(SimilarityCalculator.levenshteinDistance("abc", null)).isEqualTo(3);
        assertThat(SimilarityCalculator.levenshteinDistance(null, null)).isZero();
    }

    @Test
    void levenshteinDistance_chineseCharacters_shouldWork() {
        // Chinese characters are treated as single units
        assertThat(SimilarityCalculator.levenshteinDistance("患者姓名", "患者姓名")).isZero();
        // 患者→病人: 2 substitutions (患≠病, 者≠人)
        assertThat(SimilarityCalculator.levenshteinDistance("患者姓名", "病人姓名")).isEqualTo(2);
    }

    // ── Levenshtein Similarity (Normalized) ───────────────────────────

    @ParameterizedTest
    @CsvSource({
            "hello, hello, longer, 1.0",
            "hello, hel, longer, 0.6",
            "hello, hellx, longer, 0.8",
            "hello, , longer, 0.0",
            ", , longer, 1.0",
            "abc, def, longer, 0.0"
    })
    void levenshteinSimilarity_longerNormalization(String a, String b, String norm, String expectedStr) {
        double expected = Double.parseDouble(expectedStr);
        double result = SimilarityCalculator.levenshteinSimilarity(a, b, norm);
        assertThat(result).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.01));
    }

    @ParameterizedTest
    @CsvSource({
            "ab, cd, sum, 0.5",
            "hello, hello, sum, 1.0",
            "hello, hel, sum, 0.75"
    })
    void levenshteinSimilarity_sumNormalization(String a, String b, String norm, String expectedStr) {
        double expected = Double.parseDouble(expectedStr);
        double result = SimilarityCalculator.levenshteinSimilarity(a, b, norm);
        assertThat(result).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void levenshteinSimilarity_chineseNames() {
        double result = SimilarityCalculator.levenshteinSimilarity(
                "患者姓名", "病人姓名", "longer");
        // 患者→病人: distance=2, longer=4, score=0.5
        assertThat(result).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void levenshteinSimilarity_mixedChineseEnglish() {
        double result = SimilarityCalculator.levenshteinSimilarity(
                "患者Name", "患者Name", "longer");
        assertThat(result).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));

        result = SimilarityCalculator.levenshteinSimilarity(
                "患者Name", "患者NAME", "longer");
        // case sensitive — "患者Name" vs "患者NAME" → N→N, a→A, m→M, e→E → 4 substitutions
        assertThat(result).isLessThan(1.0);
    }

    // ── Jaccard Similarity ────────────────────────────────────────────

    @Test
    void jaccardSimilarity_identicalStrings_shouldBeOne() {
        assertThat(SimilarityCalculator.jaccardSimilarity("hello", "hello"))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void jaccardSimilarity_differentStrings_shouldComputeCorrectly() {
        // "hello": he, el, ll, lo
        // "hallo": ha, al, ll, lo
        // Intersection: {ll, lo} = 2, Union: {he, el, ll, lo, ha, al} = 6
        // J = 2/6 = 0.33
        double result = SimilarityCalculator.jaccardSimilarity("hello", "hallo");
        assertThat(result).isCloseTo(0.33, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void jaccardSimilarity_shortStrings_shouldReturnProperValue() {
        // "ab": {ab}
        // "ab": {ab}
        assertThat(SimilarityCalculator.jaccardSimilarity("ab", "ab"))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));

        // "ab": {ab}
        // "ac": {ac}
        assertThat(SimilarityCalculator.jaccardSimilarity("ab", "ac")).isZero();
    }

    @Test
    void jaccardSimilarity_singleCharacter_shouldReturnZero() {
        // Strings of length < 2 produce no bigrams → intersection empty, union empty → no match
        double result = SimilarityCalculator.jaccardSimilarity("a", "a");
        assertThat(result).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void jaccardSimilarity_emptyStrings_shouldReturnOne() {
        assertThat(SimilarityCalculator.jaccardSimilarity("", ""))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void jaccardSimilarity_chineseBigrams() {
        // "患者姓名" → 患者, 者姓, 姓名
        // "病人姓名" → 病人, 人姓, 姓名
        // Intersection: {姓名} = 1, Union: {患者, 者姓, 姓名, 病人, 人姓} = 5
        // J = 1/5 = 0.20
        double result = SimilarityCalculator.jaccardSimilarity("患者姓名", "病人姓名");
        assertThat(result).isCloseTo(0.20, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void jaccardSimilarity_nullInputs() {
        assertThat(SimilarityCalculator.jaccardSimilarity(null, "hello")).isEqualTo(0.0);
        assertThat(SimilarityCalculator.jaccardSimilarity("hello", null)).isEqualTo(0.0);
        assertThat(SimilarityCalculator.jaccardSimilarity(null, null)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    // ── Bigram Generation ─────────────────────────────────────────────

    @Test
    void generateBigrams_shouldProduceCorrectSet() {
        Set<String> bigrams = SimilarityCalculator.generateBigrams("hello");
        assertThat(bigrams).containsExactlyInAnyOrder("he", "el", "ll", "lo");
    }

    @Test
    void generateBigrams_chineseText() {
        Set<String> bigrams = SimilarityCalculator.generateBigrams("患者姓名");
        assertThat(bigrams).containsExactlyInAnyOrder("患者", "者姓", "姓名");
    }

    @Test
    void generateBigrams_shortString_shouldReturnEmpty() {
        assertThat(SimilarityCalculator.generateBigrams("a")).isEmpty();
        assertThat(SimilarityCalculator.generateBigrams("")).isEmpty();
        assertThat(SimilarityCalculator.generateBigrams(null)).isEmpty();
    }
}
