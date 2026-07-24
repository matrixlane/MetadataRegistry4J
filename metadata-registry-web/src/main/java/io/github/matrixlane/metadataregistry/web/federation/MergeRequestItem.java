package io.github.matrixlane.metadataregistry.web.federation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * 合并请求中的条目（含传入快照与裁决结果）。
 */
@Entity
@Table(name = "merge_request_item")
@Getter
@Setter
@NoArgsConstructor
public class MergeRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merge_request_id", nullable = false)
    private MergeRequest mergeRequest;

    /**
     * 对端逻辑身份（来源 RAI + 来源标识符）。
     */
    @Column(name = "remote_item_key", nullable = false, length = 256)
    private String remoteItemKey;

    @Column(name = "remote_item_uuid")
    private UUID remoteItemUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private MergeItemChangeType changeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", nullable = false, length = 20)
    private MergeItemResolution resolution = MergeItemResolution.PENDING;

    /**
     * 匹配确认后链接到的本地条目。
     */
    @Column(name = "linked_local_item_id")
    private UUID linkedLocalItemId;

    /**
     * 传入条目完整快照（JSON）。
     */
    @Lob
    @Column(name = "payload_json")
    private String payloadJson;

    /**
     * 合并基快照（上次并入时的源端版本，三方比对用）。
     */
    @Lob
    @Column(name = "base_snapshot_json")
    private String baseSnapshotJson;
}
