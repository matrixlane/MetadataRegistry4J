package io.github.matrixlane.metadataregistry.web.federation;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 联邦节点视图（不含凭据）。
 */
public record RegistryNodeView(
        UUID id,
        String name,
        String baseUrl,
        String rai,
        boolean enabled,
        LocalDateTime createdAt
) {
    public static RegistryNodeView of(RegistryNode node) {
        return new RegistryNodeView(
                node.getId(), node.getName(), node.getBaseUrl(), node.getRai(),
                node.isEnabled(), node.getCreatedAt());
    }
}
