package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExactIdentifierMatchStrategyTest {

    private ExactIdentifierMatchStrategy strategy;
    private UUID itemA;
    private UUID itemB;
    private List<LocalItemInfo> candidates;
    private InteropElement source;

    @BeforeEach
    void setUp() {
        strategy = new ExactIdentifierMatchStrategy();
        itemA = UUID.randomUUID();
        itemB = UUID.randomUUID();

        candidates = List.of(
                new LocalItemInfo(itemA, "Patient Name", List.of("DE01.01.001.00", "PAT001")),
                new LocalItemInfo(itemB, "Diagnosis Code", List.of("DE02.01.001.00"))
        );
    }

    @Test
    void match_shouldReturnEmpty_whenSourceHasNoIdentifier() {
        source = new InteropElement(UUID.randomUUID(), null, "Test Element");
        assertThat(strategy.match(source, candidates)).isEmpty();

        source = new InteropElement(UUID.randomUUID(), "", "Test Element");
        assertThat(strategy.match(source, candidates)).isEmpty();
    }

    @Test
    void match_shouldReturnEmpty_whenNoCandidates() {
        source = new InteropElement(UUID.randomUUID(), "DE01.01.001.00", "Test");
        assertThat(strategy.match(source, List.of())).isEmpty();
    }

    @Test
    void match_shouldFindExactMatch() {
        source = new InteropElement(UUID.randomUUID(), "DE01.01.001.00", "Test");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).localItemId()).isEqualTo(itemA);
        assertThat(results.get(0).confidenceScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(results.get(0).strategy()).isEqualTo(MatchStrategyType.EXACT_IDENTIFIER);
        assertThat(results.get(0).reason()).contains("EXACT_IDENTIFIER");
    }

    @Test
    void match_shouldBeCaseInsensitive() {
        source = new InteropElement(UUID.randomUUID(), "de01.01.001.00", "Test");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).localItemId()).isEqualTo(itemA);
    }

    @Test
    void match_shouldIgnoreSpecialCharacters() {
        source = new InteropElement(UUID.randomUUID(), "DE01 01-001/00", "Test");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).localItemId()).isEqualTo(itemA);
    }

    @Test
    void match_shouldReturnEmpty_whenNoIdentifierMatches() {
        source = new InteropElement(UUID.randomUUID(), "DE99.99.999.00", "Unknown");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).isEmpty();
    }

    @Test
    void match_shouldHandleMultipleIdentifiers() {
        // Source matches PAT001 (second identifier of itemA)
        source = new InteropElement(UUID.randomUUID(), "PAT001", "Test");
        List<MatchCandidate> results = strategy.match(source, candidates);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).localItemId()).isEqualTo(itemA);
    }

    @Test
    void match_shouldHandleMixedChineseIdentifier() {
        UUID itemC = UUID.randomUUID();
        List<LocalItemInfo> chineseCandidates = List.of(
                new LocalItemInfo(itemC, "患者姓名", List.of("数据元-001"))
        );
        source = new InteropElement(UUID.randomUUID(), "数据元001", "患者姓名");

        List<MatchCandidate> results = strategy.match(source, chineseCandidates);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).localItemId()).isEqualTo(itemC);
    }

    @Test
    void match_shouldReturnMultipleMatches_whenMultipleItemsMatch() {
        // Both items have matching identifiers
        UUID itemC = UUID.randomUUID();
        List<LocalItemInfo> multiMatchCandidates = List.of(
                new LocalItemInfo(itemA, "A", List.of("MATCH001")),
                new LocalItemInfo(itemB, "B", List.of("MATCH001"))
        );
        source = new InteropElement(UUID.randomUUID(), "MATCH001", "Test");

        List<MatchCandidate> results = strategy.match(source, multiMatchCandidates);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> c.confidenceScore() == 1.00);
    }

    @Test
    void getType_shouldReturnExactIdentifier() {
        assertThat(strategy.getType()).isEqualTo(MatchStrategyType.EXACT_IDENTIFIER);
    }
}
