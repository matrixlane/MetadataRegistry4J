package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.entity.DefinitionAuditLog;
import io.github.matrixlane.metadataregistry.web.repository.DefinitionAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Definition 操作日志服务。
 */
@Service
@RequiredArgsConstructor
public class DefinitionAuditLogService {

    private final DefinitionAuditLogRepository auditLogRepository;

    @Transactional
    public DefinitionAuditLog save(DefinitionAuditLog log) {
        return auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<DefinitionAuditLog> findByDefinitionId(UUID definitionId) {
        return auditLogRepository.findByDefinitionIdOrderByOperatedAtDesc(definitionId);
    }

    @Transactional(readOnly = true)
    public List<DefinitionAuditLog> findByItemId(UUID itemId) {
        return auditLogRepository.findByItemIdOrderByOperatedAtDesc(itemId);
    }
}
