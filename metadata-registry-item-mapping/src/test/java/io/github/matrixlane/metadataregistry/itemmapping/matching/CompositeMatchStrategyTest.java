package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeMatchStrategyTest {

    private CompositeMatchStrategy strategy;
    private MatchConfiguration config;
    private UUID itemA;  // Has matching identifier
    private UUID itemB;  // Has matching name
    private UUID itemC;  // Non-matching
    private List<LocalItemInfo> candidates;

    @BeforeEach
    void setUp() {
        config = new MatchConfiguration();
        config.setLevenshteinWeight(0.6);
        config.setJaccardWeight(0.4);
        config.setMaxCandidatesPerElement(3);

        strategy = new CompositeMatchStrategy(config);

        itemA = UUID.randomUUID();
        itemB = UUID.randomUUID();
        itemC = UUID.randomUUID();

        candidates = List.of(
                new LocalItemInfo(itemA, "Patient Name", List.of("DE01.01.001.00")),
                new LocalItemInfo(itemB, "Diagnosis Code", List.of("DE02.01.001.00")),
                new LocalItemInfo(itemC, "Blood Type", List.of("DE03.01.001.00"))
        );
    }

    @Test
    void match_shouldReturnEmpty_whenNoSource() {
        assertThat(strategy.match(null, candidates)).isEmpty();
    }

    @Test
    void match_shouldReturnEmpty_whenNoCandidates() {
        InteropElement source = new InteropElement(UUID.randomUUID(), "DE01.01.001.00", "Patient Name");
        assertThat(strategy.match(source, List.of())).isEmpty();
    }

    @Test
    void match_exactIdentifier_shouldHavePriority() {
        // Source matches itemA by identifier AND itemB by name
        // Identifier match should win (priority)
        InteropElement source = new InteropElement(UUID.randomUUID(), "DE01.01.001.00", "Diagnosis Code");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).isNotEmpty();
        // itemA matched by identifier should be top (score=1.00)
        assertThat(results.get(0).localItemId()).isEqualTo(itemA);
        assertThat(results.get(0).confidenceScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(results.get(0).strategy()).isEqualTo(MatchStrategyType.EXACT_IDENTIFIER);
    }

    @Test
    void match_shouldDeduplicateSameItemFromMultipleStrategies() {
        // Source matches itemA by BOTH identifier and name
        // Should only appear once (deduplicated)
        InteropElement source = new InteropElement(UUID.randomUUID(), "DE01.01.001.00", "Patient Name");
        List<MatchCandidate> results = strategy.match(source, candidates);

        // Only one result for itemA
        long itemACount = results.stream()
                .filter(c -> c.localItemId().equals(itemA))
                .count();
        assertThat(itemACount).isEqualTo(1);
    }

    @Test
    void match_shouldReturnTop3Candidates() {
        InteropElement source = new InteropElement(UUID.randomUUID(), "DE01.01.001.00", "Patient Name");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void match_shouldIncludeNameSimilarity_whenNoIdentifierMatch() {
        InteropElement source = new InteropElement(UUID.randomUUID(), "DE99.99.999.00", "Patient Name");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).isNotEmpty();
        // itemA should be matched by name
        assertThat(results.get(0).localItemId()).isEqualTo(itemA);
        assertThat(results.get(0).strategy()).isEqualTo(MatchStrategyType.NAME_SIMILARITY);
    }

    @Test
    void match_shouldReturnEmpty_whenNothingMatches() {
        InteropElement source = new InteropElement(UUID.randomUUID(), "DE99.99.999.00", "Completely Unknown");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).isEmpty();
    }

    @Test
    void match_shouldSortByConfidenceDescending() {
        InteropElement source = new InteropElement(UUID.randomUUID(), "DE01.01.001.00", "Patient Name");
        List<MatchCandidate> results = strategy.match(source, candidates);

        for (int i = 0; i < results.size() - 1; i++) {
            assertThat(results.get(i).confidenceScore())
                    .isGreaterThanOrEqualTo(results.get(i + 1).confidenceScore());
        }
    }

    @Test
    void match_shouldIncludeAllCandidates_whenAllHaveSimilarNames() {
        UUID itemD = UUID.randomUUID();
        UUID itemE = UUID.randomUUID();
        List<LocalItemInfo> similarCandidates = List.of(
                new LocalItemInfo(itemD, "Patient Name Record", List.of()),
                new LocalItemInfo(itemE, "Patient Name History", List.of())
        );

        InteropElement source = new InteropElement(UUID.randomUUID(), "ID001", "Patient Name");
        List<MatchCandidate> results = strategy.match(source, similarCandidates);

        assertThat(results).hasSize(2);
    }

    @Test
    void match_configurableMaxCandidates() {
        config.setMaxCandidatesPerElement(1);
        strategy = new CompositeMatchStrategy(config);

        InteropElement source = new InteropElement(UUID.randomUUID(), "DE01.01.001.00", "Patient Name");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).hasSize(1);
    }

    @Test
    void getType_shouldReturnComposite() {
        assertThat(strategy.getType()).isEqualTo(MatchStrategyType.COMPOSITE);
    }

    @Test
    void getStrategies_shouldReturnTwoDefaultStrategies() {
        assertThat(strategy.getStrategies()).hasSize(2);
        assertThat(strategy.getStrategies().get(0).getType()).isEqualTo(MatchStrategyType.EXACT_IDENTIFIER);
        assertThat(strategy.getStrategies().get(1).getType()).isEqualTo(MatchStrategyType.NAME_SIMILARITY);
    }
}
