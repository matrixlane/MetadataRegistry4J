package io.github.matrixlane.metadataregistry.itemmapping.service;

import io.github.matrixlane.metadataregistry.itemmapping.entity.InteropMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.InteropMappingStatus;
import io.github.matrixlane.metadataregistry.itemmapping.repository.InteropMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing interoperability mapping results (WI-013-2b).
 *
 * <p>Provides operations for:
 * <ul>
 *   <li>Listing and filtering mapping suggestions by dataset, status, confidence</li>
 *   <li>Manual confirmation, rejection, and remapping</li>
 *   <li>Undo operations for individual and batch actions</li>
 *   <li>Batch confirm/reject with full transaction rollback</li>
 *   <li>Alignment quality score calculation</li>
 *   <li>Empty state detection and guidance</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class InteropMappingResultService {

    private final InteropMappingRepository interopMappingRepository;

    // ── List & Filter ───────────────────────────────────────────────

    /**
     * Lists mapping suggestions for a dataset with optional filters.
     *
     * @param datasetId     the dataset UUID
     * @param status        optional status filter
     * @param minConfidence optional minimum confidence score
     * @param maxConfidence optional maximum confidence score
     * @param pageable      pagination parameters
     * @return page of matching mappings
     */
    @Transactional(readOnly = true)
    public Page<InteropMapping> listMappings(UUID datasetId,
                                              InteropMappingStatus status,
                                              BigDecimal minConfidence,
                                              BigDecimal maxConfidence,
                                              Pageable pageable) {
        return interopMappingRepository.findByDatasetAndFilters(
                datasetId, status, minConfidence, maxConfidence, pageable);
    }

    /**
     * Gets a single mapping by ID.
     *
     * @param id the mapping UUID
     * @return the mapping
     * @throws IllegalArgumentException if not found
     */
    @Transactional(readOnly = true)
    public InteropMapping getMapping(UUID id) {
        return interopMappingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + id));
    }

    // ── Manual Confirm / Reject / Remap / Undo ──────────────────────

    /**
     * Confirms a PENDING mapping.
     *
     * @param mappingId the mapping UUID
     * @param userId    the user confirming
     * @return the updated mapping
     * @throws IllegalArgumentException if mapping not found or not in PENDING state
     * @throws OptimisticLockingFailureException if concurrent modification detected
     */
    @Transactional
    public InteropMapping confirmMapping(UUID mappingId, UUID userId) {
        InteropMapping mapping = getMapping(mappingId);
        if (mapping.getStatus() != InteropMappingStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Only PENDING mappings can be confirmed. Current status: " + mapping.getStatus());
        }
        mapping.confirm(userId);
        return interopMappingRepository.save(mapping);
    }

    /**
     * Rejects a PENDING mapping.
     *
     * @param mappingId the mapping UUID
     * @param userId    the user rejecting
     * @return the updated mapping
     * @throws IllegalArgumentException if mapping not found or not in PENDING state
     * @throws OptimisticLockingFailureException if concurrent modification detected
     */
    @Transactional
    public InteropMapping rejectMapping(UUID mappingId, UUID userId) {
        InteropMapping mapping = getMapping(mappingId);
        if (mapping.getStatus() != InteropMappingStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Only PENDING mappings can be rejected. Current status: " + mapping.getStatus());
        }
        mapping.reject(userId);
        return interopMappingRepository.save(mapping);
    }

    /**
     * Manually remaps an interop element to a different local item.
     *
     * @param mappingId    the mapping UUID
     * @param newLocalItemId the new local item UUID
     * @param userId       the user performing the remap
     * @return the updated mapping
     * @throws IllegalArgumentException if mapping not found
     * @throws OptimisticLockingFailureException if concurrent modification detected
     */
    @Transactional
    public InteropMapping manualMap(UUID mappingId, UUID newLocalItemId, UUID userId) {
        InteropMapping mapping = getMapping(mappingId);
        mapping.manualMap(newLocalItemId, userId);
        return interopMappingRepository.save(mapping);
    }

    /**
     * Undoes the last state transition on a mapping.
     *
     * @param mappingId the mapping UUID
     * @return the restored mapping
     * @throws IllegalArgumentException if mapping not found or has no previous state
     * @throws OptimisticLockingFailureException if concurrent modification detected
     */
    @Transactional
    public InteropMapping undoMapping(UUID mappingId) {
        InteropMapping mapping = getMapping(mappingId);
        if (mapping.getPreviousStatus() == null) {
            throw new IllegalArgumentException("Mapping has no previous state to undo to: " + mappingId);
        }
        mapping.undo();
        return interopMappingRepository.save(mapping);
    }

    /**
     * Marks a mapping as UNMATCHED (no corresponding local item).
     *
     * @param mappingId the mapping UUID
     * @return the updated mapping
     * @throws IllegalArgumentException if mapping not found
     * @throws OptimisticLockingFailureException if concurrent modification detected
     */
    @Transactional
    public InteropMapping markUnmatched(UUID mappingId) {
        InteropMapping mapping = getMapping(mappingId);
        mapping.markUnmatched();
        return interopMappingRepository.save(mapping);
    }

    // ── Batch Operations ────────────────────────────────────────────

    /**
     * Counts PENDING mappings in a dataset before batch operation.
     *
     * @param datasetId the dataset UUID
     * @return count of pending mappings
     */
    @Transactional(readOnly = true)
    public long countPendingByDataset(UUID datasetId) {
        return interopMappingRepository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.PENDING);
    }

    /**
     * Batch confirms all PENDING mappings in a dataset.
     *
     * <p>Uses a batch operation ID to enable undo. All updates are within
     * a single transaction — any failure rolls back the entire batch.
     *
     * @param datasetId the dataset UUID
     * @param userId    the user performing the batch confirm
     * @return the batch operation ID for potential undo
     * @throws IllegalArgumentException if no pending mappings exist
     */
    @Transactional
    public UUID batchConfirm(UUID datasetId, UUID userId) {
        List<InteropMapping> pending = interopMappingRepository
                .findByDatasetIdAndStatusOrderByConfidenceScoreDesc(datasetId, InteropMappingStatus.PENDING);

        if (pending.isEmpty()) {
            throw new IllegalArgumentException("No PENDING mappings found in dataset: " + datasetId);
        }

        UUID batchOperationId = UUID.randomUUID();

        for (InteropMapping mapping : pending) {
            mapping.setBatchOperationId(batchOperationId);
            mapping.confirm(userId);
        }

        interopMappingRepository.saveAll(pending);
        return batchOperationId;
    }

    /**
     * Batch rejects all PENDING mappings in a dataset.
     *
     * <p>Uses a batch operation ID to enable undo. All updates are within
     * a single transaction — any failure rolls back the entire batch.
     *
     * @param datasetId the dataset UUID
     * @param userId    the user performing the batch reject
     * @return the batch operation ID for potential undo
     * @throws IllegalArgumentException if no pending mappings exist
     */
    @Transactional
    public UUID batchReject(UUID datasetId, UUID userId) {
        List<InteropMapping> pending = interopMappingRepository
                .findByDatasetIdAndStatusOrderByConfidenceScoreDesc(datasetId, InteropMappingStatus.PENDING);

        if (pending.isEmpty()) {
            throw new IllegalArgumentException("No PENDING mappings found in dataset: " + datasetId);
        }

        UUID batchOperationId = UUID.randomUUID();

        for (InteropMapping mapping : pending) {
            mapping.setBatchOperationId(batchOperationId);
            mapping.reject(userId);
        }

        interopMappingRepository.saveAll(pending);
        return batchOperationId;
    }

    /**
     * Undoes an entire batch operation.
     *
     * @param batchOperationId the batch operation UUID
     * @return number of mappings restored
     * @throws IllegalArgumentException if batch operation not found
     */
    @Transactional
    public int undoBatchOperation(UUID batchOperationId) {
        List<InteropMapping> mappings = interopMappingRepository.findByBatchOperationId(batchOperationId);

        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("Batch operation not found: " + batchOperationId);
        }

        for (InteropMapping mapping : mappings) {
            mapping.undo();
        }

        interopMappingRepository.saveAll(mappings);
        return mappings.size();
    }

    // ── Quality Score ───────────────────────────────────────────────

    /**
     * Calculates the alignment quality score for a dataset.
     *
     * <p>Formula: (matched * 1.0 + pending * 0.5) / total * 100
     * <ul>
     *   <li>AUTO_MATCHED and CONFIRMED count as matched (weight 1.0)</li>
     *   <li>PENDING counts as half-matched (weight 0.5)</li>
     *   <li>REJECTED and UNMATCHED count as unmatched (weight 0.0)</li>
     * </ul>
     *
     * @param datasetId the dataset UUID
     * @return quality score result
     */
    @Transactional(readOnly = true)
    public QualityScoreResult calculateQualityScore(UUID datasetId) {
        long total = interopMappingRepository.countByDatasetId(datasetId);

        if (total == 0) {
            return new QualityScoreResult(datasetId, 0, 0, 0, 0, 0.0);
        }

        long matched = interopMappingRepository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.AUTO_MATCHED)
                + interopMappingRepository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.CONFIRMED);
        long pending = interopMappingRepository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.PENDING);
        long unmatched = interopMappingRepository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.REJECTED)
                + interopMappingRepository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.UNMATCHED);

        double score = ((matched * 1.0) + (pending * 0.5)) / total * 100.0;
        score = BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new QualityScoreResult(
                datasetId,
                (int) total,
                (int) matched,
                (int) pending,
                (int) unmatched,
                score
        );
    }

    /**
     * Result of a quality score calculation.
     */
    public record QualityScoreResult(
            UUID datasetId,
            int totalCount,
            int matchedCount,
            int pendingCount,
            int unmatchedCount,
            double score
    ) {}

    // ── Empty State Detection ───────────────────────────────────────

    /**
     * Checks whether a dataset has any mappings.
     *
     * @param datasetId the dataset UUID
     * @return true if the dataset has no mappings
     */
    @Transactional(readOnly = true)
    public boolean isDatasetEmpty(UUID datasetId) {
        return interopMappingRepository.countByDatasetId(datasetId) == 0;
    }

    /**
     * Checks whether there are any local items referenced in a dataset's mappings.
     *
     * @param datasetId the dataset UUID
     * @return true if the dataset has mappings but no local items (empty registry)
     */
    @Transactional(readOnly = true)
    public boolean hasEmptyLocalMetadata(UUID datasetId) {
        long total = interopMappingRepository.countByDatasetId(datasetId);
        long unmatched = interopMappingRepository.countByDatasetIdAndStatus(datasetId, InteropMappingStatus.UNMATCHED);
        return total > 0 && total == unmatched;
    }

    /**
     * Gets the status distribution for a dataset.
     *
     * @param datasetId the dataset UUID
     * @return map of status to count
     */
    @Transactional(readOnly = true)
    public Map<InteropMappingStatus, Long> getStatusDistribution(UUID datasetId) {
        List<InteropMapping> mappings = interopMappingRepository.findByDatasetId(datasetId);
        return mappings.stream()
                .collect(Collectors.groupingBy(
                        InteropMapping::getStatus,
                        Collectors.counting()
                ));
    }
}
