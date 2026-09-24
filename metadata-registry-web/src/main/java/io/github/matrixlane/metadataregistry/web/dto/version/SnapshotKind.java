package io.github.matrixlane.metadataregistry.web.dto.version;

/**
 * 版本历史快照类别。
 *
 * <p>写入 {@code version_history.snapshot_json} 的 {@code meta.kind} 字段，
 * 描述该快照在管理项生命周期中的语义，不影响既有 {@code change_type} 列。</p>
 */
public enum SnapshotKind {
    /** 管理项从非草稿进入草稿的编辑前一刻完整状态，作为回退目标。 */
    BASELINE,
    /** 草稿期编辑保存生成的快照。 */
    DRAFT_EDIT,
    /** 创建、提交或批准后的已提交状态快照。 */
    SUBMITTED,
    /** 回退操作生成的快照。 */
    ROLLED_BACK
}
