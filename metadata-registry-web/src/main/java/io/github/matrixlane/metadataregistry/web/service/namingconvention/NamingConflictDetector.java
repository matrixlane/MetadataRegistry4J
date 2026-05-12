package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
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
}
