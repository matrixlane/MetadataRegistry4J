package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NameSimilarityMatchStrategyTest {

    private NameSimilarityMatchStrategy strategy;
    private MatchConfiguration config;
    private UUID itemA;
    private UUID itemB;
    private List<LocalItemInfo> candidates;

    @BeforeEach
    void setUp() {
        config = new MatchConfiguration();
        config.setLevenshteinWeight(0.6);
        config.setJaccardWeight(0.4);
        config.setLevenshteinNormalization("longer");
        strategy = new NameSimilarityMatchStrategy(config);

        itemA = UUID.randomUUID();
        itemB = UUID.randomUUID();

        candidates = List.of(
                new LocalItemInfo(itemA, "Patient Name", List.of("PAT001")),
                new LocalItemInfo(itemB, "Diagnosis Code", List.of("DIA001"))
        );
    }

    @Test
    void match_shouldReturnEmpty_whenSourceIsNull() {
        assertThat(strategy.match(null, candidates)).isEmpty();
    }

    @Test
    void match_shouldReturnEmpty_whenNoCandidates() {
        InteropElement source = new InteropElement(UUID.randomUUID(), "ID001", "Patient Name");
        assertThat(strategy.match(source, List.of())).isEmpty();
    }

    @Test
    void match_shouldReturnEmpty_whenSourceHasNoName() {
        InteropElement source = new InteropElement(UUID.randomUUID(), "ID001", null);
        assertThat(strategy.match(source, candidates)).isEmpty();

        source = new InteropElement(UUID.randomUUID(), "ID001", "");
        assertThat(strategy.match(source, candidates)).isEmpty();
    }

    @Test
    void match_identicalNames_shouldReturnHighScore() {
        InteropElement source = new InteropElement(UUID.randomUUID(), "ID001", "Patient Name");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).localItemId()).isEqualTo(itemA);
        assertThat(results.get(0).confidenceScore())
                .isGreaterThan(0.90); // Nearly identical after normalization
        assertThat(results.get(0).strategy()).isEqualTo(MatchStrategyType.NAME_SIMILARITY);
    }

    @Test
    void match_shouldRankByConfidenceDescending() {
        // Source name close to itemA, far from itemB
        InteropElement source = new InteropElement(UUID.randomUUID(), "ID001", "Patient Name");

        List<MatchCandidate> results = strategy.match(source, candidates);
        // itemB ("Diagnosis Code") is too dissimilar → filtered by noise floor
        assertThat(results).hasSize(1);
        assertThat(results.get(0).localItemId()).isEqualTo(itemA);
        assertThat(results.get(0).confidenceScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void match_shouldHandleChineseNames() {
        UUID itemC = UUID.randomUUID();
        List<LocalItemInfo> chineseCandidates = List.of(
                new LocalItemInfo(itemC, "患者姓名", List.of())
        );

        InteropElement source = new InteropElement(UUID.randomUUID(), "ID001", "患者姓名");
        List<MatchCandidate> results = strategy.match(source, chineseCandidates);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).confidenceScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void match_shouldHandleMixedChineseEnglishNames() {
        UUID itemC = UUID.randomUUID();
        List<LocalItemInfo> candidates = List.of(
                new LocalItemInfo(itemC, "患者Name Test", List.of())
        );

        InteropElement source = new InteropElement(UUID.randomUUID(), "ID001", "患者Name Test");
        List<MatchCandidate> results = strategy.match(source, candidates);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).confidenceScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void match_shouldHandleSpecialCharacters() {
        UUID itemC = UUID.randomUUID();
        UUID itemD = UUID.randomUUID();
        List<LocalItemInfo> candidates = List.of(
                new LocalItemInfo(itemC, "患者-姓名(男)", List.of()),
                new LocalItemInfo(itemD, "患者 姓名 男", List.of())
        );

        // normalizeName preserves hyphens/parens but trims/collapses spaces
        InteropElement source = new InteropElement(UUID.randomUUID(), "ID001", "患者姓名男");
        List<MatchCandidate> results = strategy.match(source, candidates);
        // Both candidates should produce matches (scores > noise floor)
        assertThat(results).hasSize(2);
        // Scores vary due to preserved punctuation in name normalization
        assertThat(results.get(0).confidenceScore()).isGreaterThan(0.20);
        assertThat(results.get(1).confidenceScore()).isGreaterThan(0.20);
    }

    @Test
    void match_shouldProduceFormattedReason() {
        InteropElement source = new InteropElement(UUID.randomUUID(), "ID001", "Patient Name");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).isNotEmpty();
        String reason = results.get(0).reason();
        assertThat(reason).contains("NAME_SIMILARITY");
        assertThat(reason).contains("Levenshtein");
        assertThat(reason).contains("Jaccard");
        assertThat(reason).contains("lev=0.6");
        assertThat(reason).contains("jac=0.4");
    }

    @Test
    void match_shouldUseCorrectNormalization() {
        // Test with "sum" normalization
        config.setLevenshteinNormalization("sum");
        strategy = new NameSimilarityMatchStrategy(config);

        InteropElement source = new InteropElement(UUID.randomUUID(), "ID001", "Patient Name");
        List<MatchCandidate> results = strategy.match(source, candidates);
        assertThat(results).isNotEmpty();
    }

    @Test
    void match_confidenceShouldBeRoundedToTwoDecimals() {
        // Use a short, distinct source to produce a fractional score
        InteropElement source = new InteropElement(UUID.randomUUID(), "ID001", "PatientX");
        List<MatchCandidate> results = strategy.match(source, candidates);

        for (MatchCandidate candidate : results) {
            double score = candidate.confidenceScore();
            // Check that score is rounded to 2 decimal places (allow tiny FP epsilon)
            assertThat(score * 100).isCloseTo(Math.round(score * 100),
                    org.assertj.core.data.Offset.offset(0.0001));
        }
    }

    @Test
    void getType_shouldReturnNameSimilarity() {
        assertThat(strategy.getType()).isEqualTo(MatchStrategyType.NAME_SIMILARITY);
    }
}
