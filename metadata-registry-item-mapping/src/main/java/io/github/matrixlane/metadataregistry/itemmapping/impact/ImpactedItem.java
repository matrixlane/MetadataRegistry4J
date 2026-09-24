package io.github.matrixlane.metadataregistry.itemmapping.impact;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;

import java.util.UUID;

/**
 * 受映射变更影响的单个条目。
 *
 * @param itemId          受影响条目的 UUID
 * @param impactLevel     影响级别（direct / indirect / cascade）
 * @param impactDirection 影响方向
 * @param mappingId       造成影响的映射 UUID
 * @param degree          映射度
 * @param suggestedAction 可读的处置建议
 */
public record ImpactedItem(
        UUID itemId,
        ImpactLevel impactLevel,
        ImpactDirection impactDirection,
        UUID mappingId,
        MappingDegree degree,
        String suggestedAction
) {
}
