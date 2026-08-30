package io.github.matrixlane.metadataregistry.web.dto.association;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 焦点实体的一条关联记录。
 *
 * @param relationType       标准关联类型（{@link StandardAssociationType} 枚举名）
 * @param direction          相对焦点实体的方向：OUTGOING（焦点 → 对方）/ INCOMING（对方 → 焦点）
 * @param itemId             对方实体的 Item.id
 * @param itemType           对方实体类型（DataElement / DataElementConcept / ConceptualDomain / ValueDomain）
 * @param label              对方实体偏好指称
 * @param identifier         对方实体业务标识符
 * @param version            对方实体版本号
 * @param creationDateTime   对方实体创建时间
 * @param lastChangeDateTime 对方实体最后编辑时间
 */
public record ItemAssociationEntry(
        String relationType,
        String direction,
        UUID itemId,
        String itemType,
        String label,
        String identifier,
        String version,
        LocalDateTime creationDateTime,
        LocalDateTime lastChangeDateTime) {

    public static final String OUTGOING = "OUTGOING";
    public static final String INCOMING = "INCOMING";
}
