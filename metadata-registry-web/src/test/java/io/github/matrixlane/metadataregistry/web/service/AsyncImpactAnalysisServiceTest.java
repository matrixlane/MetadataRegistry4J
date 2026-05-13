package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisResult;
import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncImpactAnalysisServiceTest {

    @Mock
    private ImpactAnalysisService impactAnalysisService;

    private AsyncImpactAnalysisService asyncService;

    @BeforeEach
    void setUp() {
        asyncService = new AsyncImpactAnalysisService(impactAnalysisService);
    }

    @Test
    void analyzeItemChangeAsync_shouldReturnCompletableFuture() throws ExecutionException, InterruptedException {
        UUID itemId = UUID.randomUUID();
        ImpactAnalysisResult expectedResult = new ImpactAnalysisResult(
                itemId, "UPDATED", List.of(), 0, false);

        when(impactAnalysisService.analyzeItemChange(itemId, "UPDATED"))
                .thenReturn(expectedResult);

        CompletableFuture<ImpactAnalysisResult> future =
                asyncService.analyzeItemChangeAsync(itemId, "UPDATED");

        assertThat(future).isCompleted();
        ImpactAnalysisResult result = future.get();
        assertThat(result.changedItemId()).isEqualTo(itemId);
        assertThat(result.changeType()).isEqualTo("UPDATED");
    }

    @Test
    void analyzeMappingChangeAsync_shouldReturnCompletableFuture() throws ExecutionException, InterruptedException {
        UUID mappingId = UUID.randomUUID();
        ImpactAnalysisResult expectedResult = new ImpactAnalysisResult(
                mappingId, "DELETED", List.of(), 0, false);

        when(impactAnalysisService.analyzeMappingChange(mappingId, "DELETED"))
                .thenReturn(expectedResult);

        CompletableFuture<ImpactAnalysisResult> future =
                asyncService.analyzeMappingChangeAsync(mappingId, "DELETED");

        assertThat(future).isCompleted();
        ImpactAnalysisResult result = future.get();
        assertThat(result.changedItemId()).isEqualTo(mappingId);
        assertThat(result.changeType()).isEqualTo("DELETED");
    }

    @Test
    void analyzeItemChangeAsync_whenServiceThrows_shouldReturnFailedFuture() {
        UUID itemId = UUID.randomUUID();
        RuntimeException exception = new RuntimeException("Analysis failed");

        when(impactAnalysisService.analyzeItemChange(itemId, "UPDATED"))
                .thenThrow(exception);

        CompletableFuture<ImpactAnalysisResult> future =
                asyncService.analyzeItemChangeAsync(itemId, "UPDATED");

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Analysis failed");
    }

    @Test
    void analyzeMappingChangeAsync_whenServiceThrows_shouldReturnFailedFuture() {
        UUID mappingId = UUID.randomUUID();
        RuntimeException exception = new RuntimeException("Analysis failed");

        when(impactAnalysisService.analyzeMappingChange(mappingId, "MODIFIED"))
                .thenThrow(exception);

        CompletableFuture<ImpactAnalysisResult> future =
                asyncService.analyzeMappingChangeAsync(mappingId, "MODIFIED");

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Analysis failed");
    }
}
