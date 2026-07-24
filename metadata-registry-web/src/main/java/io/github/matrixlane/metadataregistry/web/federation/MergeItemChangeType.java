package io.github.matrixlane.metadataregistry.web.federation;

public enum MergeItemChangeType {
    /** 新增（无链接、无匹配） */
    ADD,
    /** 已链接，仅源侧变更，干净更新 */
    UPDATE,
    /** 已链接，双侧变更，需评审裁决 */
    CONFLICT,
    /** 首次合并的身份匹配候选 */
    MATCH_CANDIDATE
}
