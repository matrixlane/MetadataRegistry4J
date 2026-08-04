package io.github.matrixlane.metadataregistry.web.federation;

import java.util.UUID;

/**
 * 合并请求条目视图（评审工作台用，不含懒加载代理与超大 payload）。
 *
 * <p>冲突条目附带版本三方信息：来源版本、合并基（上次迁移的来源版本）与
 * 目的方本地版本，供 Registrar 裁决时参考。
 */
public record MergeRequestItemView(
        UUID id,
        String remoteItemKey,
        UUID remoteItemUuid,
        MergeItemChangeType changeType,
        MergeItemResolution resolution,
        UUID linkedLocalItemId,
        String itemLabel,
        String sourceVersion,
        String baseVersion,
        String localVersion
) {
    public static MergeRequestItemView of(MergeRequestItem item) {
        String label = null;
        try {
            if (item.getPayloadJson() != null) {
                var node = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(item.getPayloadJson());
                label = node.path("export").path("itemLabel").asText(null);
            }
        } catch (Exception ignored) {
        }
        return new MergeRequestItemView(
                item.getId(),
                item.getRemoteItemKey(),
                item.getRemoteItemUuid(),
                item.getChangeType(),
                item.getResolution(),
                item.getLinkedLocalItemId(),
                label,
                null,
                null,
                null);
    }

    /**
     * 补充版本三方信息（冲突条目用）。
     */
    public MergeRequestItemView withVersions(String sourceVersion, String baseVersion, String localVersion) {
        return new MergeRequestItemView(id, remoteItemKey, remoteItemUuid, changeType, resolution,
                linkedLocalItemId, itemLabel, sourceVersion, baseVersion, localVersion);
    }
}
