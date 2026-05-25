package io.github.matrixlane.metadataregistry.web.dto.readiness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST API response for readiness check.
 */
public record ReadinessChecklistResponse(
        UUID namespaceId,
        String namespaceName,
        boolean allReady,
        boolean registrationAuthorityExists,
        boolean namespaceExists,
        boolean namespaceActive,
        boolean namingConventionValid,
        boolean userHasPermission,
        List<String> missingPrerequisites,
        List<String> guidance,
        LocalDateTime checkedAt
) {
    public static ReadinessChecklistResponse from(UUID namespaceId, ReadinessChecklist checklist,
                                                  String namespaceName, List<String> guidance) {
        return new ReadinessChecklistResponse(
                namespaceId,
                namespaceName,
                checklist.isAllReady(),
                checklist.registrationAuthorityExists(),
                checklist.namespaceExists(),
                checklist.namespaceActive(),
                checklist.namingConventionValid(),
                checklist.userHasPermission(),
                checklist.missingPrerequisites(),
                guidance,
                LocalDateTime.now()
        );
    }
}