package io.github.matrixlane.metadataregistry.web.dto.readiness;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * WI-028-C Phase 2: DataGovernanceReadinessService 就绪检查清单
 *
 * <p>包含 Namespace 就绪状态的所有检查项，以及缺失前置条件的列表。
 * 用于引导用户在创建 Item 前补齐缺失的前置条件。
 */
public record ReadinessChecklist(
        UUID namespaceId,
        boolean registrationAuthorityExists,
        boolean registrySpecificationExists,
        boolean namespaceExists,
        boolean namespaceActive,
        boolean namingConventionValid,
        boolean userHasPermission,
        List<String> missingPrerequisites
) {
    public ReadinessChecklist {
        if (missingPrerequisites == null) missingPrerequisites = new ArrayList<>();
    }

    public boolean isAllReady() {
        return registrationAuthorityExists
                && registrySpecificationExists
                && namespaceExists
                && namespaceActive
                && namingConventionValid
                && userHasPermission;
    }
}