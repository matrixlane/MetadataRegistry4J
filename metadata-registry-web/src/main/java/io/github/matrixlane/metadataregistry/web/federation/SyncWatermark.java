package io.github.matrixlane.metadataregistry.web.federation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 同步水位线：按对端节点记录最近一次成功合并的位置，
 * 使下一次合并请求只包含增量。
 */
@Entity
@Table(name = "sync_watermark")
@Getter
@Setter
@NoArgsConstructor
public class SyncWatermark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false, unique = true)
    private RegistryNode node;

    @Column(name = "last_merged_mr_id")
    private UUID lastMergedMrId;

    /**
     * 最近成功合并对应的源端最大 version_number（按条目合并基推导用）。
     */
    @Column(name = "last_merged_version")
    private Integer lastMergedVersion;

    @Column(name = "last_merged_at")
    private LocalDateTime lastMergedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
