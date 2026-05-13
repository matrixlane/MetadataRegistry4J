package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for the matching engine.
 *
 * <p>Validates that concurrent invocation by 10 users does not affect
 * matching accuracy (WI-013-2a acceptance criterion).
 */
class MatchingEngineConcurrencyTest {

    private MatchingEngineService service;
    private MatchConfiguration config;
    private List<InteropElement> sources;
    private List<LocalItemInfo> candidates;

    @BeforeEach
    void setUp() {
        config = new MatchConfiguration();
        config.setLevenshteinWeight(0.6);
        config.setJaccardWeight(0.4);
        config.setMaxCandidatesPerElement(3);
        service = new MatchingEngineService(config, new NameIndexCache());

        // Build deterministic test data
        sources = new ArrayList<>();
        candidates = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID localId = UUID.randomUUID();
            String identifier = "DE01.01." + String.format("%03d", i);
            sources.add(new InteropElement(UUID.randomUUID(), identifier, "Name" + i));
            candidates.add(new LocalItemInfo(localId, "LocalName" + i, List.of(identifier)));
        }
    }

    @Test
    void concurrentMatchOne_10Threads_shouldProduceConsistentResults() throws InterruptedException, ExecutionException {
        InteropElement source = sources.get(0);
        int threadCount = 10;
        int iterationsPerThread = 100;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<List<MatchSuggestion>>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            futures.add(executor.submit(() -> {
                List<MatchSuggestion> results = new ArrayList<>();
                for (int i = 0; i < iterationsPerThread; i++) {
                    results.add(service.matchOne(source, candidates));
                }
                return results;
            }));
        }

        // Collect all results
        List<MatchSuggestion> allResults = new ArrayList<>();
        for (Future<List<MatchSuggestion>> future : futures) {
            allResults.addAll(future.get());
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // All results should be identical
        assertThat(allResults).hasSize(threadCount * iterationsPerThread);

        MatchSuggestion first = allResults.get(0);
        assertThat(first.topCandidates()).isNotEmpty();
        assertThat(first.bestCandidate().confidenceScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));

        for (MatchSuggestion result : allResults) {
            assertThat(result.topCandidates().size()).isEqualTo(first.topCandidates().size());
            if (!result.topCandidates().isEmpty()) {
                assertThat(result.bestCandidate().localItemId()).isEqualTo(first.bestCandidate().localItemId());
                assertThat(result.bestCandidate().confidenceScore()).isEqualTo(first.bestCandidate().confidenceScore());
            }
        }
    }

    @Test
    void concurrentMatchAll_10Threads_shouldProduceConsistentResults() throws InterruptedException, ExecutionException {
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<List<MatchSuggestion>>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            futures.add(executor.submit(() -> service.matchAll(sources, candidates)));
        }

        List<List<MatchSuggestion>> allRuns = new ArrayList<>();
        for (Future<List<MatchSuggestion>> future : futures) {
            allRuns.add(future.get());
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(allRuns).hasSize(threadCount);

        List<MatchSuggestion> firstRun = allRuns.get(0);
        assertThat(firstRun).hasSize(sources.size());

        for (List<MatchSuggestion> run : allRuns) {
            assertThat(run).hasSize(firstRun.size());
            for (int i = 0; i < run.size(); i++) {
                MatchSuggestion expected = firstRun.get(i);
                MatchSuggestion actual = run.get(i);
                assertThat(actual.topCandidates().size()).isEqualTo(expected.topCandidates().size());
                if (!actual.topCandidates().isEmpty() && !expected.topCandidates().isEmpty()) {
                    assertThat(actual.bestCandidate().localItemId()).isEqualTo(expected.bestCandidate().localItemId());
                    assertThat(actual.bestCandidate().confidenceScore()).isEqualTo(expected.bestCandidate().confidenceScore());
                }
            }
        }
    }

    @Test
    void concurrentIndexBuildAndMatch_shouldNotCorruptResults() throws InterruptedException, ExecutionException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            futures.add(executor.submit(() -> {
                // Alternate between rebuild and match
                for (int i = 0; i < 50; i++) {
                    if (threadIndex % 2 == 0) {
                        service.buildIndex(candidates);
                    } else {
                        service.matchAll(sources, candidates);
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Final match should still be correct
        List<MatchSuggestion> results = service.matchAll(sources, candidates);
        assertThat(results).hasSize(sources.size());
        // Exact identifier matches should still produce score 1.00
        for (int i = 0; i < results.size(); i++) {
            assertThat(results.get(i).bestCandidate()).isNotNull();
            assertThat(results.get(i).bestCandidate().confidenceScore()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        }
    }
}
