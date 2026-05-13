package io.github.matrixlane.metadataregistry.itemmapping.service;

import io.github.matrixlane.metadataregistry.itemmapping.entity.InteropMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.InteropMappingStatus;
import io.github.matrixlane.metadataregistry.itemmapping.repository.InteropMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteropMappingResultServiceTest {

    @Mock
    private InteropMappingRepository repository;

    private InteropMappingResultService service;

    @BeforeEach
    void setUp() {
        service = new InteropMappingResultService(repository);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private InteropMapping createMapping(UUID id, InteropMappingStatus status, BigDecimal score) {
        InteropMapping mapping = new InteropMapping(
                UUID.randomUUID(),
                UUID.randomUUID(),
                score,
                "NAME_SIMILARITY",
                status,
                "reason",
                UUID.randomUUID()
        );
        mapping.setId(id);
        mapping.setVersion(1L);
        return mapping;
    }

    // ── List & Filter Tests ─────────────────────────────────────────

    @Test
    void listMappings_shouldReturnFilteredPage() {
        UUID datasetId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        InteropMapping mapping = createMapping(UUID.randomUUID(), InteropMappingStatus.PENDING, BigDecimal.valueOf(0.75));
        Page<InteropMapping> page = new PageImpl<>(List.of(mapping));

        when(repository.findByDatasetAndFilters(datasetId, InteropMappingStatus.PENDING,
                BigDecimal.valueOf(0.60), BigDecimal.valueOf(0.85), pageable))
                .thenReturn(page);

        Page<InteropMapping> result = service.listMappings(datasetId, InteropMappingStatus.PENDING,
                BigDecimal.valueOf(0.60), BigDecimal.valueOf(0.85), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(InteropMappingStatus.PENDING);
    }

    @Test
    void getMapping_shouldReturnMappingWhenFound() {
        UUID id = UUID.randomUUID();
        InteropMapping mapping = createMapping(id, InteropMappingStatus.PENDING, BigDecimal.valueOf(0.75));
        when(repository.findById(id)).thenReturn(Optional.of(mapping));

        InteropMapping result = service.getMapping(id);

        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    void getMapping_shouldThrowWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMapping(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mapping not found");
    }

    // ── Confirm Tests ───────────────────────────────────────────────

    @Test
    void confirmMapping_shouldChangeStatusToConfirmed() {
        UUID mappingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InteropMapping mapping = createMapping(mappingId, InteropMappingStatus.PENDING, BigDecimal.valueOf(0.75));
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteropMapping result = service.confirmMapping(mappingId, userId);

        assertThat(result.getStatus()).isEqualTo(InteropMappingStatus.CONFIRMED);
        assertThat(result.getConfirmedBy()).isEqualTo(userId);
        assertThat(result.getPreviousStatus()).isEqualTo(InteropMappingStatus.PENDING);
    }

    @Test
    void confirmMapping_shouldThrowWhenNotPending() {
        UUID mappingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InteropMapping mapping = createMapping(mappingId, InteropMappingStatus.CONFIRMED, BigDecimal.valueOf(0.75));
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> service.confirmMapping(mappingId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only PENDING mappings can be confirmed");
    }

    // ── Reject Tests ────────────────────────────────────────────────

    @Test
    void rejectMapping_shouldChangeStatusToRejected() {
        UUID mappingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InteropMapping mapping = createMapping(mappingId, InteropMappingStatus.PENDING, BigDecimal.valueOf(0.75));
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteropMapping result = service.rejectMapping(mappingId, userId);

        assertThat(result.getStatus()).isEqualTo(InteropMappingStatus.REJECTED);
        assertThat(result.getConfirmedBy()).isEqualTo(userId);
        assertThat(result.getPreviousStatus()).isEqualTo(InteropMappingStatus.PENDING);
    }

    @Test
    void rejectMapping_shouldThrowWhenNotPending() {
        UUID mappingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InteropMapping mapping = createMapping(mappingId, InteropMappingStatus.REJECTED, BigDecimal.valueOf(0.75));
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> service.rejectMapping(mappingId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only PENDING mappings can be rejected");
    }

    // ── Manual Map Tests ────────────────────────────────────────────

    @Test
    void manualMap_shouldChangeLocalItemIdAndStatus() {
        UUID mappingId = UUID.randomUUID();
        UUID newLocalItemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID originalLocalItemId = UUID.randomUUID();
        InteropMapping mapping = createMapping(mappingId, InteropMappingStatus.PENDING, BigDecimal.valueOf(0.75));
        mapping.setLocalItemId(originalLocalItemId);
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteropMapping result = service.manualMap(mappingId, newLocalItemId, userId);

        assertThat(result.getLocalItemId()).isEqualTo(newLocalItemId);
        assertThat(result.getPreviousLocalItemId()).isEqualTo(originalLocalItemId);
        assertThat(result.getStatus()).isEqualTo(InteropMappingStatus.CONFIRMED);
    }

    // ── Undo Tests ──────────────────────────────────────────────────

    @Test
    void undoMapping_shouldRestorePreviousStatus() {
        UUID mappingId = UUID.randomUUID();
        InteropMapping mapping = createMapping(mappingId, InteropMappingStatus.CONFIRMED, BigDecimal.valueOf(0.75));
        mapping.setPreviousStatus(InteropMappingStatus.PENDING);
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteropMapping result = service.undoMapping(mappingId);

        assertThat(result.getStatus()).isEqualTo(InteropMappingStatus.PENDING);
        assertThat(result.getPreviousStatus()).isNull();
        assertThat(result.getConfirmedBy()).isNull();
    }

    @Test
    void undoMapping_shouldThrowWhenNoPreviousStatus() {
        UUID mappingId = UUID.randomUUID();
        InteropMapping mapping = createMapping(mappingId, InteropMappingStatus.PENDING, BigDecimal.valueOf(0.75));
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> service.undoMapping(mappingId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no previous state to undo");
    }

    // ── Mark Unmatched Tests ────────────────────────────────────────

    @Test
    void markUnmatched_shouldChangeStatusToUnmatched() {
        UUID mappingId = UUID.randomUUID();
        InteropMapping mapping = createMapping(mappingId, InteropMappingStatus.PENDING, BigDecimal.valueOf(0.75));
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InteropMapping result = service.markUnmatched(mappingId);

        assertThat(result.getStatus()).isEqualTo(InteropMappingStatus.UNMATCHED);
    }

    // ── Batch Confirm Tests ─────────────────────────────────────────

    @Test
    void batchConfirm_shouldConfirmAllPending() {
        UUID datasetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InteropMapping m1 = createMapping(UUID.randomUUID(), InteropMappingStatus.PENDING, BigDecimal.valueOf(0.80));
        InteropMapping m2 = createMapping(UUID.randomUUID(), InteropMappingStatus.PENDING, BigDecimal.valueOf(0.70));
        when(repository.findByDatasetIdAndStatusOrderByConfidenceScoreDesc(datasetId, InteropMappingStatus.PENDING))
                .thenReturn(List.of(m1, m2));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID batchId = service.batchConfirm(datasetId, userId);

        assertThat(batchId).isNotNull();
        assertThat(m1.getStatus()).isEqualTo(InteropMappingStatus.CONFIRMED);
        assertThat(m2.getStatus()).isEqualTo(InteropMappingStatus.CONFIRMED);
        assertThat(m1.getBatchOperationId()).isEqualTo(batchId);
        verify(repository).saveAll(any());
    }

    @Test
    void batchConfirm_shouldThrowWhenNoPending() {
        UUID datasetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.findByDatasetIdAndStatusOrderByConfidenceScoreDesc(datasetId, InteropMappingStatus.PENDING))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.batchConfirm(datasetId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No PENDING mappings found");
    }

    // ── Batch Reject Tests ──────────────────────────────────────────

    @Test
    void batchReject_shouldRejectAllPending() {
        UUID datasetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InteropMapping m1 = createMapping(UUID.randomUUID(), InteropMappingStatus.PENDING, BigDecimal.valueOf(0.80));
        when(repository.findByDatasetIdAndStatusOrderByConfidenceScoreDesc(datasetId, InteropMappingStatus.PENDING))
                .thenReturn(List.of(m1));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID batchId = service.batchReject(datasetId, userId);

        assertThat(m1.getStatus()).isEqualTo(InteropMappingStatus.REJECTED);
        assertThat(m1.getBatchOperationId()).isEqualTo(batchId);
    }

    @Test
    void batchReject_shouldThrowWhenNoPending() {
        UUID datasetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.findByDatasetIdAndStatusOrderByConfidenceScoreDesc(datasetId, InteropMappingStatus.PENDING))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.batchReject(datasetId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No PENDING mappings found");
    }

    // ── Undo Batch Tests ────────────────────────────────────────────

    @Test
    void undoBatchOperation_shouldRestoreAllMappings() {
        UUID batchId = UUID.randomUUID();
        InteropMapping m1 = createMapping(UUID.randomUUID(), InteropMappingStatus.CONFIRMED, BigDecimal.valueOf(0.80));
        m1.setBatchOperationId(batchId);
        m1.setPreviousStatus(InteropMappingStatus.PENDING);
        when(repository.findByBatchOperationId(batchId)).thenReturn(List.of(m1));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        int count = service.undoBatchOperation(batchId);

        assertThat(count).isEqualTo(1);
        assertThat(m1.getStatus()).isEqualTo(InteropMappingStatus.PENDING);
        assertThat(m1.getBatchOperationId()).isNull();
    }

    @Test
    void undoBatchOperation_shouldThrowWhenBatchNotFound() {
        UUID batchId = UUID.randomUUID();
        when(repository.findByBatchOperationId(batchId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.undoBatchOperation(batchId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch operation not found");
    }

    // ── Quality Score Tests ─────────────────────────────────────────

    @Test
    void calculateQualityScore_shouldReturnCorrectScore() {
        UUID datasetId = UUID.randomUUID();
        when(repository.countByDatasetId(datasetId)).thenReturn(10L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.AUTO_MATCHED)).thenReturn(3L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.CONFIRMED)).thenReturn(2L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.PENDING)).thenReturn(3L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.REJECTED)).thenReturn(1L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.UNMATCHED)).thenReturn(1L);

        var result = service.calculateQualityScore(datasetId);

        // (5*1.0 + 3*0.5) / 10 * 100 = 65.0
        assertThat(result.totalCount()).isEqualTo(10);
        assertThat(result.matchedCount()).isEqualTo(5);
        assertThat(result.pendingCount()).isEqualTo(3);
        assertThat(result.unmatchedCount()).isEqualTo(2);
        assertThat(result.score()).isEqualTo(65.0);
    }

    @Test
    void calculateQualityScore_shouldReturnZeroWhenEmpty() {
        UUID datasetId = UUID.randomUUID();
        when(repository.countByDatasetId(datasetId)).thenReturn(0L);

        var result = service.calculateQualityScore(datasetId);

        assertThat(result.totalCount()).isEqualTo(0);
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void calculateQualityScore_shouldReturn100WhenAllMatched() {
        UUID datasetId = UUID.randomUUID();
        when(repository.countByDatasetId(datasetId)).thenReturn(5L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.AUTO_MATCHED)).thenReturn(3L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.CONFIRMED)).thenReturn(2L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.PENDING)).thenReturn(0L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.REJECTED)).thenReturn(0L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.UNMATCHED)).thenReturn(0L);

        var result = service.calculateQualityScore(datasetId);

        assertThat(result.score()).isEqualTo(100.0);
    }

    // ── Empty State Tests ───────────────────────────────────────────

    @Test
    void isDatasetEmpty_shouldReturnTrueWhenNoMappings() {
        UUID datasetId = UUID.randomUUID();
        when(repository.countByDatasetId(datasetId)).thenReturn(0L);

        assertThat(service.isDatasetEmpty(datasetId)).isTrue();
    }

    @Test
    void isDatasetEmpty_shouldReturnFalseWhenMappingsExist() {
        UUID datasetId = UUID.randomUUID();
        when(repository.countByDatasetId(datasetId)).thenReturn(5L);

        assertThat(service.isDatasetEmpty(datasetId)).isFalse();
    }

    @Test
    void hasEmptyLocalMetadata_shouldReturnTrueWhenAllUnmatched() {
        UUID datasetId = UUID.randomUUID();
        when(repository.countByDatasetId(datasetId)).thenReturn(5L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.UNMATCHED)).thenReturn(5L);

        assertThat(service.hasEmptyLocalMetadata(datasetId)).isTrue();
    }

    @Test
    void hasEmptyLocalMetadata_shouldReturnFalseWhenSomeMatched() {
        UUID datasetId = UUID.randomUUID();
        when(repository.countByDatasetId(datasetId)).thenReturn(5L);
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.UNMATCHED)).thenReturn(2L);

        assertThat(service.hasEmptyLocalMetadata(datasetId)).isFalse();
    }

    @Test
    void hasEmptyLocalMetadata_shouldReturnFalseWhenNoMappings() {
        UUID datasetId = UUID.randomUUID();
        when(repository.countByDatasetId(datasetId)).thenReturn(0L);

        assertThat(service.hasEmptyLocalMetadata(datasetId)).isFalse();
    }

    // ── Count Pending Tests ─────────────────────────────────────────

    @Test
    void countPendingByDataset_shouldReturnCount() {
        UUID datasetId = UUID.randomUUID();
        when(repository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.PENDING)).thenReturn(7L);

        assertThat(service.countPendingByDataset(datasetId)).isEqualTo(7L);
    }

    // ── Status Distribution Tests ───────────────────────────────────

    @Test
    void getStatusDistribution_shouldReturnCountsByStatus() {
        UUID datasetId = UUID.randomUUID();
        InteropMapping m1 = createMapping(UUID.randomUUID(), InteropMappingStatus.AUTO_MATCHED, BigDecimal.valueOf(0.90));
        InteropMapping m2 = createMapping(UUID.randomUUID(), InteropMappingStatus.PENDING, BigDecimal.valueOf(0.75));
        InteropMapping m3 = createMapping(UUID.randomUUID(), InteropMappingStatus.PENDING, BigDecimal.valueOf(0.70));
        when(repository.findByDatasetId(datasetId)).thenReturn(List.of(m1, m2, m3));

        var distribution = service.getStatusDistribution(datasetId);

        assertThat(distribution).containsEntry(InteropMappingStatus.AUTO_MATCHED, 1L);
        assertThat(distribution).containsEntry(InteropMappingStatus.PENDING, 2L);
    }
}
