package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisResult;
import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous wrapper for mapping impact analysis.
 *
 * <p>For large mapping networks ({@code > 10,000} mappings), impact analysis
 * is offloaded to an async executor to avoid blocking the request thread.
 *
 * <p>V1: Uses the existing {@link ImpactAnalysisService} with BFS traversal.
 * Future versions may switch to recursive CTE or graph database queries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncImpactAnalysisService {

    private final ImpactAnalysisService impactAnalysisService;

    /**
     * Asynchronously analyzes the impact of an item change.
     *
     * @param itemId     the UUID of the changed item
     * @param changeType description of the change
     * @return a CompletableFuture containing the impact analysis result
     */
    @Async("dataQualityTaskExecutor")
    public CompletableFuture<ImpactAnalysisResult> analyzeItemChangeAsync(UUID itemId, String changeType) {
        log.debug("Starting async impact analysis for item {} change {}", itemId, changeType);
        long start = System.currentTimeMillis();
        try {
            ImpactAnalysisResult result = impactAnalysisService.analyzeItemChange(itemId, changeType);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("Async impact analysis completed in {} ms, impacted {} items", elapsed, result.totalImpacted());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Async impact analysis failed for item {}", itemId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Asynchronously analyzes the impact of a mapping change.
     *
     * @param mappingId  the UUID of the changed mapping
     * @param changeType description of the change
     * @return a CompletableFuture containing the impact analysis result
     */
    @Async("dataQualityTaskExecutor")
    public CompletableFuture<ImpactAnalysisResult> analyzeMappingChangeAsync(UUID mappingId, String changeType) {
        log.debug("Starting async impact analysis for mapping {} change {}", mappingId, changeType);
        long start = System.currentTimeMillis();
        try {
            ImpactAnalysisResult result = impactAnalysisService.analyzeMappingChange(mappingId, changeType);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("Async impact analysis completed in {} ms, impacted {} items", elapsed, result.totalImpacted());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Async impact analysis failed for mapping {}", mappingId, e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
