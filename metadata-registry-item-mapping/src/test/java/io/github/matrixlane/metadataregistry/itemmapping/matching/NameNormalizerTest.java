package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameNormalizerTest {

    // ── Name Normalization ────────────────────────────────────────────

    @Test
    void normalizeName_shouldTrimWhitespace() {
        assertThat(NameNormalizer.normalizeName("  患者姓名  ")).isEqualTo("患者姓名");
    }

    @Test
    void normalizeName_shouldCollapseMultipleSpaces() {
        assertThat(NameNormalizer.normalizeName("患者  姓名")).isEqualTo("患者 姓名");
    }

    @Test
    void normalizeName_shouldLowercase() {
        assertThat(NameNormalizer.normalizeName("Patient Name")).isEqualTo("patient name");
    }

    @Test
    void normalizeName_shouldHandleChineseCharacters() {
        String result = NameNormalizer.normalizeName("患者姓名");
        assertThat(result).isEqualTo("患者姓名");
    }

    @Test
    void normalizeName_shouldHandleMixedChineseEnglish() {
        String result = NameNormalizer.normalizeName("患者Name Test");
        assertThat(result).isEqualTo("患者name test");
    }

    @Test
    void normalizeName_shouldReturnEmptyForNull() {
        assertThat(NameNormalizer.normalizeName(null)).isEmpty();
    }

    @Test
    void normalizeName_shouldReturnEmptyForEmptyString() {
        assertThat(NameNormalizer.normalizeName("")).isEmpty();
    }

    @Test
    void normalizeName_shouldHandleSpecialCharacters() {
        String result = NameNormalizer.normalizeName("患者-姓名(男)/Test");
        // spaces, hyphens, parentheses, slashes are preserved in name normalization
        assertThat(result).isEqualTo("患者-姓名(男)/test");
    }

    // ── Identifier Normalization ──────────────────────────────────────

    @Test
    void normalizeIdentifier_shouldRemoveSpecialChars() {
        assertThat(NameNormalizer.normalizeIdentifier("DE01.01.001.00"))
                .isEqualTo("de010100100");
    }

    @Test
    void normalizeIdentifier_shouldHandleHyphens() {
        assertThat(NameNormalizer.normalizeIdentifier("DE01-01-001"))
                .isEqualTo("de0101001");
    }

    @Test
    void normalizeIdentifier_shouldHandleMixed() {
        assertThat(NameNormalizer.normalizeIdentifier("DE 01.01 (001)/A"))
                .isEqualTo("de0101001a");
    }

    @Test
    void normalizeIdentifier_differentFormats_shouldMatch() {
        String a = NameNormalizer.normalizeIdentifier("DE01.01.001");
        String b = NameNormalizer.normalizeIdentifier("DE01-01-001");
        String c = NameNormalizer.normalizeIdentifier("DE 01 01 001");
        assertThat(a).isEqualTo(b).isEqualTo(c);
    }

    @Test
    void normalizeIdentifier_shouldHandleChineseIdentifier() {
        assertThat(NameNormalizer.normalizeIdentifier("数据元-001"))
                .isEqualTo("数据元001");
    }

    @Test
    void normalizeIdentifier_shouldReturnEmptyForNull() {
        assertThat(NameNormalizer.normalizeIdentifier(null)).isEmpty();
    }

    @Test
    void normalizeIdentifier_shouldReturnEmptyForEmpty() {
        assertThat(NameNormalizer.normalizeIdentifier("")).isEmpty();
    }
}
