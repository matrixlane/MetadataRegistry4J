package io.github.matrixlane.metadataregistry.web.dto.association;

import java.util.List;
import java.util.UUID;

/**
 * 焦点实体在四实体标准关联上的全部正/反向实例。
 */
public record ItemAssociationsResponse(
        UUID itemId,
        String itemType,
        List<ItemAssociationEntry> associations) {
}
