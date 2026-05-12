package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.web.entity.ApprovalProcessInstance;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalProcessStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalProcessInstanceRepository extends JpaRepository<ApprovalProcessInstance, UUID> {

    Optional<ApprovalProcessInstance> findFirstByAdministeredItemIdAndStatusOrderByCreatedAtDesc(
            UUID administeredItemId, ApprovalProcessStatus status);

    boolean existsByAdministeredItemIdAndStatus(UUID administeredItemId, ApprovalProcessStatus status);

    List<ApprovalProcessInstance> findByStatus(ApprovalProcessStatus status);

    Optional<ApprovalProcessInstance> findByIdAndStatus(UUID id, ApprovalProcessStatus status);
}
