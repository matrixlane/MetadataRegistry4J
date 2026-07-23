package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 命名冲突检测器。
 *
 * <p>检测同一 Namespace 内是否存在重复的 Sign 值。
 */
@Component
@RequiredArgsConstructor
public class NamingConflictDetector {

    private final DesignationRepository designationRepository;
    private final ScopedIdentifierRepository scopedIdentifierRepository;

    /**
     * 检查给定的 Sign 在指定 Namespace 内是否已存在（排除指定 Item）。
     *
     * @param namespaceId Namespace ID
     * @param sign        待检查的名称
     * @param excludeItemId 排除的 Item ID（用于更新场景，可为 null）
     * @return 若存在冲突返回 true
     */
    public boolean hasConflict(UUID namespaceId, Sign sign, UUID excludeItemId) {
        if (namespaceId == null || sign == null || sign.getValue() == null || sign.getValue().isBlank()) {
            return false;
        }
        return designationRepository.existsByNamespaceIdAndSignValueAndItemIdNot(
                namespaceId, sign.getValue(), excludeItemId);
    }

    /**
     * 检查给定的 Sign 在指定 Namespace 内是否已存在（用于新建场景）。
     */
    public boolean hasConflict(UUID namespaceId, Sign sign) {
        return hasConflict(namespaceId, sign, null);
    }

    /**
     * 检查给定的 identifier 在指定 Namespace 内是否已存在（排除指定 IdentifiedItem）。
     *
     * @param namespaceId      Namespace ID
     * @param identifier       待检查的标识符
     * @param excludeIdentifiedItemId 排除的 IdentifiedItem ID（用于更新场景，可为 null）
     * @return 若存在冲突返回 true
     */
    public boolean hasIdentifierConflict(UUID namespaceId, String identifier, UUID excludeIdentifiedItemId) {
        if (namespaceId == null || identifier == null || identifier.isBlank()) {
            return false;
        }
        return scopedIdentifierRepository.existsByNamespaceIdAndIdentifierAndIdentifiedItemIdNot(
                namespaceId, identifier, excludeIdentifiedItemId);
    }

    /**
     * 检查给定的 identifier 在指定 Namespace 内是否已存在（用于新建场景）。
     */
    public boolean hasIdentifierConflict(UUID namespaceId, String identifier) {
        return hasIdentifierConflict(namespaceId, identifier, null);
    }
}
