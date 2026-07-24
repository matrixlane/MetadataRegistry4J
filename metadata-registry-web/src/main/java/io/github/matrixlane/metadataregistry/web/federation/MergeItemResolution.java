package io.github.matrixlane.metadataregistry.web.federation;

public enum MergeItemResolution {
    PENDING,
    /** 采用传入版本 */
    ACCEPT,
    /** 拒绝（保留现有） */
    REJECT,
    /** 与本地既有条目建立链接后接受 */
    LINK_EXISTING
}
