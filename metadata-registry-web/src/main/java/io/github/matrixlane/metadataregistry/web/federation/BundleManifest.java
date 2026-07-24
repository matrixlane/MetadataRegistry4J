package io.github.matrixlane.metadataregistry.web.federation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 合并包清单（federation bundle v1）。
 */
public record BundleManifest(
        String format,
        Instant createdAt,
        UUID sourceNodeId,
        String sourceRai,
        UUID targetNamespaceId,
        Integer watermarkVersion,
        String receiptCallback,
        List<BundleItemSnapshot> items
) {
    public static final String FORMAT_V1 = "mdr-federation-bundle-v1";
}
