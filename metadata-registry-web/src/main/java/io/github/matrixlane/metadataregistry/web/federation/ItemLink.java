package io.github.matrixlane.metadataregistry.web.federation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 跨实例条目链接（Git 跟踪引用的等价物）。
 *
 * <p>本实例视角的链接：本地条目 ↔ 对端节点的逻辑身份（来源 RAI + 来源标识符）。
 * 首次合并由评审确认建立，后续合并作为合并基定位依据。
 */
@Entity
@Table(name = "item_link")
@Getter
@Setter
@NoArgsConstructor
public class ItemLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * 本实例中的条目（Item/AdministeredItem 的 UUID）。
     */
    @Column(name = "local_item_id", nullable = false)
    private UUID localItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remote_node_id", nullable = false)
    private RegistryNode remoteNode;

    /**
     * 对端逻辑身份：来源 RAI + 来源标识符（形如 "RA-001:BUSINESS_CONTEXT"）。
     */
    @Column(name = "remote_item_key", nullable = false, length = 256)
    private String remoteItemKey;

    /**
     * 对端实例内的条目 UUID（仅供回执对照，不参与身份判定）。
     */
    @Column(name = "remote_item_uuid")
    private UUID remoteItemUuid;

    @Column(name = "established_by_mr_id")
    private UUID establishedByMrId;

    @Column(name = "established_at", nullable = false, updatable = false)
    private LocalDateTime establishedAt;

    @PrePersist
    protected void onCreate() {
        establishedAt = LocalDateTime.now();
    }
}
