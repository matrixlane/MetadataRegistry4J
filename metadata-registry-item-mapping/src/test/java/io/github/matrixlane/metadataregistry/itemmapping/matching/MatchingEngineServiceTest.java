package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineServiceTest {

    private MatchingEngineService service;
    private MatchConfiguration config;
    private UUID interopId1;
    private UUID interopId2;
    private UUID localId1;
    private UUID localId2;
    private UUID localId3;
    private List<InteropElement> sources;
    private List<LocalItemInfo> candidates;

    @BeforeEach
    void setUp() {
        config = new MatchConfiguration();
        config.setLevenshteinWeight(0.6);
        config.setJaccardWeight(0.4);
        config.setMaxCandidatesPerElement(3);

        NameIndexCache cache = new NameIndexCache();

        service = new MatchingEngineService(config, cache);

        interopId1 = UUID.randomUUID();
        interopId2 = UUID.randomUUID();
        localId1 = UUID.randomUUID();
        localId2 = UUID.randomUUID();
        localId3 = UUID.randomUUID();

        sources = List.of(
                new InteropElement(interopId1, "DE01.01.001.00", "Patient Name"),
                new InteropElement(interopId2, "DE02.01.001.00", "Diagnosis Code")
        );

        candidates = List.of(
                new LocalItemInfo(localId1, "Patient Name", List.of("DE01.01.001.00")),
                new LocalItemInfo(localId2, "Diagnosis Code", List.of("DE02.01.001.00")),
                new LocalItemInfo(localId3, "Blood Type", List.of("DE03.01.001.00"))
        );
    }

    // ── Index Management ─────────────────────────────────────────────

    @Test
    void buildIndex_shouldUpdateCache() {
        assertThat(service.getNameIndexCache().itemCount()).isZero();
        service.buildIndex(candidates);
        assertThat(service.getNameIndexCache().itemCount()).isEqualTo(3);
    }

    // ── matchOne ──────────────────────────────────────────────────────

    @Test
    void matchOne_shouldReturnSuggestion() {
        InteropElement source = sources.get(0);
        MatchSuggestion result = service.matchOne(source, candidates);

        assertThat(result.interopElementId()).isEqualTo(interopId1);
        assertThat(result.topCandidates()).isNotEmpty();
        // Should match by identifier (priority) → localId1
        assertThat(result.topCandidates().get(0).localItemId()).isEqualTo(localId1);
        assertThat(result.topCandidates().get(0).confidenceScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void matchOne_shouldReturnEmpty_whenNullSource() {
        MatchSuggestion result = service.matchOne(null, candidates);
        assertThat(result.topCandidates()).isEmpty();
    }

    @Test
    void matchOne_shouldReturnEmpty_whenNoCandidates() {
        MatchSuggestion result = service.matchOne(sources.get(0), List.of());
        assertThat(result.topCandidates()).isEmpty();
    }

    // ── matchAll ──────────────────────────────────────────────────────

    @Test
    void matchAll_shouldMatchAllSources() {
        List<MatchSuggestion> results = service.matchAll(sources, candidates);

        assertThat(results).hasSize(2);

        // First element should match by identifier
        assertThat(results.get(0).interopElementId()).isEqualTo(interopId1);
        assertThat(results.get(0).bestCandidate().localItemId()).isEqualTo(localId1);
        assertThat(results.get(0).bestCandidate().confidenceScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));

        // Second element should match by identifier
        assertThat(results.get(1).interopElementId()).isEqualTo(interopId2);
        assertThat(results.get(1).bestCandidate().localItemId()).isEqualTo(localId2);
    }

    @Test
    void matchAll_shouldHandleEmptySources() {
        List<MatchSuggestion> results = service.matchAll(List.of(), candidates);
        assertThat(results).isEmpty();
    }

    @Test
    void matchAll_shouldHandleNullSources() {
        List<MatchSuggestion> results = service.matchAll(null, candidates);
        assertThat(results).isEmpty();
    }

    @Test
    void matchAll_shouldReturnEmptySuggestions_whenNoCandidates() {
        List<MatchSuggestion> results = service.matchAll(sources, List.of());
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(s -> s.topCandidates().isEmpty());
    }

    @Test
    void matchAll_shouldBuildIndexAutomatically() {
        // Should work even without explicit buildIndex
        assertThat(service.getNameIndexCache().itemCount()).isZero();
        List<MatchSuggestion> results = service.matchAll(sources, candidates);
        assertThat(results).hasSize(2);
        // Index should be built after matchAll
        assertThat(service.getNameIndexCache().itemCount()).isEqualTo(3);
    }

    // ── matchAllFiltered ─────────────────────────────────────────────

    @Test
    void matchAllFiltered_shouldWorkWithPreFiltered() {
        List<LocalItemInfo> filteredCandidates = List.of(
                new LocalItemInfo(localId1, "Patient Name", List.of("DE01.01.001.00"))
        );

        List<MatchSuggestion> results = service.matchAllFiltered(sources, filteredCandidates, true);
        assertThat(results).hasSize(2);

        // First should have a match
        assertThat(results.get(0).topCandidates()).isNotEmpty();
        // Second (Diagnosis Code) won't match against filtered set
        assertThat(results.get(1).topCandidates()).isEmpty();
    }

    // ── Async Matching ───────────────────────────────────────────────

    @Test
    void matchAllAsync_shouldCompleteWithResults() throws ExecutionException, InterruptedException {
        CompletableFuture<List<MatchSuggestion>> future = service.matchAllAsync(sources, candidates);
        List<MatchSuggestion> results = future.get();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).bestCandidate()).isNotNull();
    }

    // ── Confidence Classification ─────────────────────────────────────

    @Test
    void classifyConfidence_high() {
        assertThat(service.classifyConfidence(0.90)).isEqualTo(MatchingEngineService.MatchLevel.HIGH);
        assertThat(service.classifyConfidence(0.85)).isEqualTo(MatchingEngineService.MatchLevel.HIGH);
    }

    @Test
    void classifyConfidence_medium() {
        assertThat(service.classifyConfidence(0.75)).isEqualTo(MatchingEngineService.MatchLevel.MEDIUM);
        assertThat(service.classifyConfidence(0.60)).isEqualTo(MatchingEngineService.MatchLevel.MEDIUM);
    }

    @Test
    void classifyConfidence_low() {
        assertThat(service.classifyConfidence(0.50)).isEqualTo(MatchingEngineService.MatchLevel.LOW);
        assertThat(service.classifyConfidence(0.0)).isEqualTo(MatchingEngineService.MatchLevel.LOW);
    }

    // ── Edge Cases ────────────────────────────────────────────────────

    @Test
    void matchAll_withNoMatchingElements_shouldReturnEmptyCandidates() {
        List<InteropElement> unknownSources = List.of(
                new InteropElement(UUID.randomUUID(), "X99.99.999", "Completely Unknown Element")
        );

        List<MatchSuggestion> results = service.matchAll(unknownSources, candidates);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).topCandidates()).isEmpty();
    }

    @Test
    void matchAll_withEmptyLocalItems_shouldHandleGracefully() {
        List<InteropElement> sources = List.of(
                new InteropElement(UUID.randomUUID(), "DE01.01.001", "Patient Name")
        );

        List<MatchSuggestion> results = service.matchAll(sources, List.of());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).topCandidates()).isEmpty();
    }

    @Test
    void matchAll_withDuplicateCandidateNames_shouldWork() {
        UUID localId4 = UUID.randomUUID();
        List<LocalItemInfo> candidatesWithDup = List.of(
                new LocalItemInfo(localId1, "Patient Name", List.of("DE01.01.001.00")),
                new LocalItemInfo(localId4, "Patient Name", List.of("DE99.99.999.00")) // same name, different identifier
        );

        List<MatchSuggestion> results = service.matchAll(sources, candidatesWithDup);
        assertThat(results).hasSize(2);

        // First result should include both items with the same name
        MatchSuggestion firstResult = results.get(0);
        assertThat(firstResult.topCandidates()).hasSize(2);
    }

    @Test
    void configuration_shouldBeAccessible() {
        assertThat(service.getConfiguration()).isNotNull();
        assertThat(service.getConfiguration().getLevenshteinWeight()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.01));
    }
}
