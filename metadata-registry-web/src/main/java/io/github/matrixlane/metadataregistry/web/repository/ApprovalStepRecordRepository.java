package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepRecord;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalStepRecordRepository extends JpaRepository<ApprovalStepRecord, UUID> {

    List<ApprovalStepRecord> findByProcessInstanceIdOrderByStepNumberAsc(UUID processInstanceId);

    Optional<ApprovalStepRecord> findFirstByProcessInstanceIdAndStepNumber(
            UUID processInstanceId, Integer stepNumber);

    @Query("SELECT sr FROM ApprovalStepRecord sr WHERE sr.processInstance.id = :processInstanceId AND sr.status = :status ORDER BY sr.stepNumber ASC")
    List<ApprovalStepRecord> findByProcessInstanceIdAndStatus(
            @Param("processInstanceId") UUID processInstanceId,
            @Param("status") ApprovalStepStatus status);

    @Query("SELECT sr FROM ApprovalStepRecord sr WHERE sr.status = :status AND sr.timeoutAt IS NOT NULL AND sr.timeoutAt < :now")
    List<ApprovalStepRecord> findTimeoutRecords(
            @Param("status") ApprovalStepStatus status,
            @Param("now") LocalDateTime now);

    @Query("SELECT sr FROM ApprovalStepRecord sr WHERE sr.processInstance.id = :processInstanceId AND sr.stepNumber = (SELECT MAX(sr2.stepNumber) FROM ApprovalStepRecord sr2 WHERE sr2.processInstance.id = :processInstanceId)")
    Optional<ApprovalStepRecord> findCurrentStepRecord(@Param("processInstanceId") UUID processInstanceId);
}
