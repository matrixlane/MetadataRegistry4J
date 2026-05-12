package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.entity.DefinitionAuditLog;
import io.github.matrixlane.metadataregistry.web.repository.DefinitionAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefinitionAuditLogServiceTest {

    @Mock
    private DefinitionAuditLogRepository auditLogRepository;

    @InjectMocks
    private DefinitionAuditLogService definitionAuditLogService;

    private UUID definitionId;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        definitionId = UUID.randomUUID();
        itemId = UUID.randomUUID();
    }

    @Test
    void findByDefinitionId_shouldReturnOrderedLogs() {
        DefinitionAuditLog log1 = createLog("CREATE");
        DefinitionAuditLog log2 = createLog("UPDATE");
        when(auditLogRepository.findByDefinitionIdOrderByOperatedAtDesc(definitionId))
                .thenReturn(List.of(log2, log1));

        List<DefinitionAuditLog> result = definitionAuditLogService.findByDefinitionId(definitionId);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAction()).isEqualTo("UPDATE");
        assertThat(result.get(1).getAction()).isEqualTo("CREATE");
    }

    @Test
    void findByItemId_shouldReturnOrderedLogs() {
        DefinitionAuditLog log1 = createLog("CREATE");
        DefinitionAuditLog log2 = createLog("DELETE");
        when(auditLogRepository.findByItemIdOrderByOperatedAtDesc(itemId))
                .thenReturn(List.of(log2, log1));

        List<DefinitionAuditLog> result = definitionAuditLogService.findByItemId(itemId);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAction()).isEqualTo("DELETE");
    }

    private DefinitionAuditLog createLog(String action) {
        DefinitionAuditLog log = new DefinitionAuditLog();
        log.setDefinitionId(definitionId);
        log.setItemId(itemId);
        log.setAction(action);
        log.setOperatedAt(LocalDateTime.now());
        return log;
    }
}
