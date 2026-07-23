
package io.github.matrixlane.metadataregistry.web.service.namespace;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConflictDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 校验 Namespace 的三大 ISO/IEC 11179-3 约束：
 * <ul>
 *   <li>mandatory_naming_convention_indicator</li>
 *   <li>one_name_per_item_indicator</li>
 *   <li>one_item_per_name_indicator</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class NamespaceConstraintValidator {

    private final NamespaceRepository namespaceRepository;
    private final DesignationRepository designationRepository;
    private final ScopedIdentifierRepository scopedIdentifierRepository;
    private final NamingConflictDetector namingConflictDetector;

    /**
     * 校验 Namespace 自身层级的约束（创建/更新 Namespace 时调用）。
     */
    public void validateNamespace(Namespace namespace) {
        if (namespace == null) {
            return;
        }
        if (Boolean.TRUE.equals(namespace.getMandatoryNamingConventionIndicator())) {
            if (namespace.getNamingConventions() == null || namespace.getNamingConventions().isEmpty()) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "Namespace 启用了 mandatoryNamingConventionIndicator，必须至少关联一个 NamingConvention");
            }
        }
    }

    /**
     * 校验某个 Designation 是否满足其所在 Namespace 的约束（创建/更新 Designation 时调用）。
     *
     * @param namespaceId   Designation 所在的 Namespace ID
     * @param designation   待校验的 Designation（可能尚未持久化）
     * @param excludeDesignationId 更新时排除自身 ID
     */
    public void validateDesignation(UUID namespaceId, Designation designation, UUID excludeDesignationId) {
        if (namespaceId == null || designation == null) {
            return;
        }
        Namespace ns = namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "Namespace 不存在: " + namespaceId));

        validateMandatoryNamingConvention(ns, designation);
        validateOneNamePerItem(ns, designation, excludeDesignationId);
        validateOneItemPerNameForDesignation(ns, designation, excludeDesignationId);
    }

    /**
     * 校验某个 ScopedIdentifier 是否满足其所在 Namespace 的约束（创建/更新 ScopedIdentifier 时调用）。
     *
     * @param namespaceId      ScopedIdentifier 所在的 Namespace ID
     * @param scopedIdentifier 待校验的 ScopedIdentifier（可能尚未持久化）
     * @param excludeIdentifiedItemId 更新时排除自身的 IdentifiedItem ID
     */
    public void validateScopedIdentifier(UUID namespaceId, ScopedIdentifier scopedIdentifier, UUID excludeIdentifiedItemId) {
        if (namespaceId == null || scopedIdentifier == null) {
            return;
        }
        Namespace ns = namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "Namespace 不存在: " + namespaceId));

        validateOneNamePerItem(ns, scopedIdentifier, excludeIdentifiedItemId);
        validateOneItemPerNameForIdentifier(ns, scopedIdentifier, excludeIdentifiedItemId);
    }

    private void validateMandatoryNamingConvention(Namespace ns, Designation designation) {
        if (!Boolean.TRUE.equals(ns.getMandatoryNamingConventionIndicator())) {
            return;
        }
        List<NamingConvention> nsConventions = ns.getNamingConventions();
        if (nsConventions == null || nsConventions.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "Namespace " + ns.getId() + " 要求强制命名规范，但未配置 NamingConvention");
        }
        if (designation.getNamingConventions() == null || designation.getNamingConventions().isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "Namespace " + ns.getId() + " 要求强制命名规范，Designation 必须关联 NamingConvention");
        }
        Set<UUID> allowedIds = nsConventions.stream()
                .map(NamingConvention::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        boolean conforms = designation.getNamingConventions().stream()
                .anyMatch(nc -> nc.getId() != null && allowedIds.contains(nc.getId()));
        if (!conforms) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "Designation 关联的 NamingConvention 不在 Namespace " + ns.getId() + " 允许的 NamingConvention 列表中");
        }
    }

    private void validateOneNamePerItem(Namespace ns, Designation designation, UUID excludeDesignationId) {
        if (!Boolean.TRUE.equals(ns.getOneNamePerItemIndicator())) {
            return;
        }
        if (designation.getItem() == null || designation.getItem().getId() == null) {
            return;
        }
        UUID itemId = designation.getItem().getId();
        List<Designation> existing = designationRepository.findByItem_Id(itemId);
        long conflictCount = existing.stream()
                .filter(d -> !d.getId().equals(excludeDesignationId))
                .filter(d -> d.getNamespaces() != null && d.getNamespaces().stream().anyMatch(n -> n.getId().equals(ns.getId())))
                .count();
        if (conflictCount > 0) {
            throw new BusinessException(ApiCodes.CONFLICT,
                    "Namespace " + ns.getId() + " 启用了 oneNamePerItemIndicator，Item " + itemId + " 已存在 Designation");
        }
    }

    private void validateOneItemPerNameForDesignation(Namespace ns, Designation designation, UUID excludeDesignationId) {
        if (!Boolean.TRUE.equals(ns.getOneItemPerNameIndicator())) {
            return;
        }
        if (designation.getSign() == null || designation.getSign().getValue() == null) {
            return;
        }
        Sign sign = designation.getSign();
        UUID excludeItemId = designation.getItem() == null ? null : designation.getItem().getId();
        if (namingConflictDetector.hasConflict(ns.getId(), sign, excludeItemId)) {
            throw new BusinessException(ApiCodes.CONFLICT,
                    "Namespace " + ns.getId() + " 启用了 oneItemPerNameIndicator，Sign [" + sign.getValue() + "] 已被其他 Item 使用");
        }
    }

    private void validateOneNamePerItem(Namespace ns, ScopedIdentifier scopedIdentifier, UUID excludeIdentifiedItemId) {
        if (!Boolean.TRUE.equals(ns.getOneNamePerItemIndicator())) {
            return;
        }
        if (scopedIdentifier.getIdentifiedItem() == null || scopedIdentifier.getIdentifiedItem().getId() == null) {
            return;
        }
        UUID identifiedItemId = scopedIdentifier.getIdentifiedItem().getId();
        List<ScopedIdentifier> existing = scopedIdentifierRepository.findByIdentifiedItemId(identifiedItemId);
        boolean conflict = existing.stream()
                .filter(si -> !si.getId().equals(scopedIdentifier.getId()))
                .filter(si -> si.getNamespace() != null && si.getNamespace().getId().equals(ns.getId()))
                .anyMatch(si -> excludeIdentifiedItemId == null ||
                        (si.getIdentifiedItem() != null && !si.getIdentifiedItem().getId().equals(excludeIdentifiedItemId)));
        if (conflict) {
            throw new BusinessException(ApiCodes.CONFLICT,
                    "Namespace " + ns.getId() + " 启用了 oneNamePerItemIndicator，IdentifiedItem " + identifiedItemId + " 已存在 ScopedIdentifier");
        }
    }

    private void validateOneItemPerNameForIdentifier(Namespace ns, ScopedIdentifier scopedIdentifier, UUID excludeIdentifiedItemId) {
        if (!Boolean.TRUE.equals(ns.getOneItemPerNameIndicator())) {
            return;
        }
        if (scopedIdentifier.getIdentifier() == null || scopedIdentifier.getIdentifier().isBlank()) {
            return;
        }
        if (namingConflictDetector.hasIdentifierConflict(ns.getId(), scopedIdentifier.getIdentifier(), excludeIdentifiedItemId)) {
            throw new BusinessException(ApiCodes.CONFLICT,
                    "Namespace " + ns.getId() + " 启用了 oneItemPerNameIndicator，Identifier [" + scopedIdentifier.getIdentifier() + "] 已被其他 IdentifiedItem 使用");
        }
    }
}
