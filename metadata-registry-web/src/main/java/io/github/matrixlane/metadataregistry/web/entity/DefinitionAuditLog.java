package io.github.matrixlane.metadataregistry.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Definition 操作日志。
 *
 * <p>V1 轻量级操作日志，记录 Definition 的创建/修改/删除操作。
 * 不记录完整 diff，仅记录操作元数据（操作人、时间、旧值摘要）。
 */
@Entity
@Table(name = "definition_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class DefinitionAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "definition_id")
    private UUID definitionId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "old_value_summary", length = 500)
    private String oldValueSummary;

    @Column(name = "new_value_summary", length = 500)
    private String newValueSummary;

    @Column(name = "language_identifier", length = 50)
    private String languageIdentifier;

    @Column(name = "operated_by", length = 100)
    private String operatedBy;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;

    public static DefinitionAuditLog ofCreate(UUID definitionId, UUID itemId,
                                               String newValueSummary, String languageIdentifier,
                                               String operatedBy) {
        DefinitionAuditLog log = new DefinitionAuditLog();
        log.setDefinitionId(definitionId);
        log.setItemId(itemId);
        log.setAction("CREATE");
        log.setNewValueSummary(newValueSummary);
        log.setLanguageIdentifier(languageIdentifier);
        log.setOperatedBy(operatedBy);
        log.setOperatedAt(LocalDateTime.now());
        return log;
    }

    public static DefinitionAuditLog ofUpdate(UUID definitionId, UUID itemId,
                                              String oldValueSummary, String newValueSummary,
                                              String languageIdentifier, String operatedBy) {
        DefinitionAuditLog log = new DefinitionAuditLog();
        log.setDefinitionId(definitionId);
        log.setItemId(itemId);
        log.setAction("UPDATE");
        log.setOldValueSummary(oldValueSummary);
        log.setNewValueSummary(newValueSummary);
        log.setLanguageIdentifier(languageIdentifier);
        log.setOperatedBy(operatedBy);
        log.setOperatedAt(LocalDateTime.now());
        return log;
    }

    public static DefinitionAuditLog ofDelete(UUID definitionId, UUID itemId,
                                              String oldValueSummary, String languageIdentifier,
                                              String operatedBy) {
        DefinitionAuditLog log = new DefinitionAuditLog();
        log.setDefinitionId(definitionId);
        log.setItemId(itemId);
        log.setAction("DELETE");
        log.setOldValueSummary(oldValueSummary);
        log.setLanguageIdentifier(languageIdentifier);
        log.setOperatedBy(operatedBy);
        log.setOperatedAt(LocalDateTime.now());
        return log;
    }
}

