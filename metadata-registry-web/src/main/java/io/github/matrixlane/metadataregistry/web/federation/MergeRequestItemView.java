package io.github.matrixlane.metadataregistry.web.federation;

import java.util.UUID;

/**
 * 合并请求条目视图（评审工作台用，不含懒加载代理与超大 payload）。
 */
public record MergeRequestItemView(
        UUID id,
        String remoteItemKey,
        UUID remoteItemUuid,
        MergeItemChangeType changeType,
        MergeItemResolution resolution,
        UUID linkedLocalItemId,
        String itemLabel
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
                label);
    }
}
