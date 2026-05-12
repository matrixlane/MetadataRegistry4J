package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.web.entity.DefinitionAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DefinitionAuditLogRepository extends JpaRepository<DefinitionAuditLog, UUID> {

    List<DefinitionAuditLog> findByDefinitionIdOrderByOperatedAtDesc(UUID definitionId);

    List<DefinitionAuditLog> findByItemIdOrderByOperatedAtDesc(UUID itemId);
}
