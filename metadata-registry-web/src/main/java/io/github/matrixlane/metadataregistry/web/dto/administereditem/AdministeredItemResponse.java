package io.github.matrixlane.metadataregistry.web.dto.administereditem;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 跨实体统一元数据搜索结果行（smart-item-picker 消费）。
 *
 * @param matchType 精确匹配度（仅 MetadataSearchService 填充）：
 *                  EXACT 精确命中；PARTIAL 模糊回退；null 既有调用方不感知
 */
public record AdministeredItemResponse(
        UUID id,
        UUID itemId,
        String itemType,
        String itemLabel,
        String identifier,
        String version,
        UUID namespaceId,
        String namespaceLabel,
        UUID registrationAuthorityId,
        String registrationAuthorityLabel,
        RegistrationStatus registrationStatus,
        LocalDateTime stateEffectiveDate,
        String administrativeNote,
        UUID constraintSetId,
        String constraintSetLabel,
        List<UUID> referenceDocumentIds,
        Boolean hasPreferred,
        LocalDateTime creationDateTime,
        LocalDateTime lastChangeDateTime,
        String changeDescription,
        String matchType) {
}