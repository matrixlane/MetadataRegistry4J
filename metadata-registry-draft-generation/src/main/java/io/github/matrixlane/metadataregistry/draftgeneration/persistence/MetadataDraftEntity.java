package io.github.matrixlane.metadataregistry.draftgeneration.persistence;

import io.github.matrixlane.metadataregistry.draftgeneration.model.DraftStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 元数据草稿实体（表级别）。
 *
 * <p>记录从数据库 Schema 提取后自动生成的元数据草稿，包含生命周期状态。
 */
@Entity
@Table(name = "metadata_draft")
@Getter
@Setter
@NoArgsConstructor
public class MetadataDraftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DraftStatus status = DraftStatus.DRAFT;

    @Column(nullable = false, length = 255)
    private String sourceDatabaseName;

    @Column(nullable = false, length = 255)
    private String sourceSchemaName;

    @Column(nullable = false, length = 255)
    private String sourceTableName;

    @Column(length = 255)
    private String suggestedTableName;

    @Column(length = 500)
    private String tableRemarks;

    @Column(nullable = false)
    private LocalDateTime extractedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "metadataDraft", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ColumnDraftEntity> columns = new ArrayList<>();

    /**
     * 构造函数。
     */
    public MetadataDraftEntity(String sourceDatabaseName, String sourceSchemaName,
                                String sourceTableName, String suggestedTableName) {
        this.sourceDatabaseName = sourceDatabaseName;
        this.sourceSchemaName = sourceSchemaName;
        this.sourceTableName = sourceTableName;
        this.suggestedTableName = suggestedTableName;
        this.status = DraftStatus.DRAFT;
        this.extractedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 添加列草稿。
     */
    public void addColumn(ColumnDraftEntity column) {
        columns.add(column);
        column.setMetadataDraft(this);
    }

    /**
     * 移除列草稿。
     */
    public void removeColumn(ColumnDraftEntity column) {
        columns.remove(column);
        column.setMetadataDraft(null);
    }

    /**
     * 状态流转验证。
     */
    public boolean canTransitionTo(DraftStatus newStatus) {
        if (this.status == newStatus) {
            return false;
        }
        return switch (this.status) {
            case DRAFT -> newStatus == DraftStatus.APPROVED || newStatus == DraftStatus.REJECTED;
            case APPROVED, REJECTED -> false;
        };
    }

    /**
     * 执行状态流转。
     */
    public void transitionTo(DraftStatus newStatus) {
        if (!canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Cannot transition from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }
}
