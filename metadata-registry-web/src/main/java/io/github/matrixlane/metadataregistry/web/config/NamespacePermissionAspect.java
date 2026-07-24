package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceWizardRequest;
import io.github.matrixlane.metadataregistry.web.entity.NamespacePermission;
import io.github.matrixlane.metadataregistry.web.repository.NamespacePermissionRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.service.NamespacePermissionEvaluator;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class NamespacePermissionAspect {

    private final NamespacePermissionEvaluator permissionEvaluator;
    private final ScopedIdentifierRepository scopedIdentifierRepository;
    private final NamespacePermissionRepository namespacePermissionRepository;

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser.id();
        }
        return null;
    }

    private boolean shouldBypass() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return true;
        }
        if ("anonymousUser".equals(auth.getPrincipal())) {
            return true;
        }
        return false;
    }

    @Before("execution(* io.github.matrixlane.metadataregistry.web.service.NamespaceService.get*(..)) && args(id, ..)")
    public void checkNamespaceGet(UUID id) {
        if (shouldBypass()) return;
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Access denied: Unauthenticated.");
        }
        if (!permissionEvaluator.hasReadPermission(id, userId)) {
            throw new AccessDeniedException("Access denied: You do not have read permission on this namespace.");
        }
    }

    @Before("execution(* io.github.matrixlane.metadataregistry.web.service.NamespaceService.update(..)) && args(id, ..)")
    public void checkNamespaceUpdate(UUID id) {
        if (shouldBypass()) return;
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Access denied: Unauthenticated.");
        }
        if (!permissionEvaluator.hasWritePermission(id, userId)) {
            throw new AccessDeniedException("Access denied: You do not have write permission on this namespace.");
        }
    }

    @Before("execution(* io.github.matrixlane.metadataregistry.web.service.NamespaceService.delete(..)) && args(id)")
    public void checkNamespaceDelete(UUID id) {
        if (shouldBypass()) return;
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Access denied: Unauthenticated.");
        }
        if (!permissionEvaluator.hasWritePermission(id, userId)) {
            throw new AccessDeniedException("Access denied: You do not have write permission on this namespace.");
        }
    }

    @Before("execution(* io.github.matrixlane.metadataregistry.web.service.NamespaceService.createWizard(..)) && args(request)")
    public void checkNamespaceCreateWizard(NamespaceWizardRequest request) {
        if (shouldBypass()) return;
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Access denied: Unauthenticated.");
        }
        if (request != null && request.parentId() != null) {
            if (!permissionEvaluator.hasWritePermission(request.parentId(), userId)) {
                throw new AccessDeniedException("Access denied: You do not have write permission on the parent namespace.");
            }
        }
    }

    @Before("execution(* io.github.matrixlane.metadataregistry.web.service.NamespacePermissionService.getPermissions(..)) && args(namespaceId)")
    public void checkGetPermissions(UUID namespaceId) {
        if (shouldBypass()) return;
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Access denied: Unauthenticated.");
        }
        if (!permissionEvaluator.hasReadPermission(namespaceId, userId)) {
            throw new AccessDeniedException("Access denied: You do not have read permission on this namespace.");
        }
    }

    @Before("execution(* io.github.matrixlane.metadataregistry.web.service.NamespacePermissionService.savePermission(..)) && args(namespaceId, ..)")
    public void checkSavePermission(UUID namespaceId) {
        if (shouldBypass()) return;
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Access denied: Unauthenticated.");
        }
        if (!permissionEvaluator.hasWritePermission(namespaceId, userId)) {
            throw new AccessDeniedException("Access denied: You do not have write permission on this namespace.");
        }
    }

    @Before("execution(* io.github.matrixlane.metadataregistry.web.service.NamespacePermissionService.deletePermission(..)) && args(id)")
    public void checkDeletePermission(UUID id) {
        if (shouldBypass()) return;
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Access denied: Unauthenticated.");
        }
        NamespacePermission perm = namespacePermissionRepository.findById(id).orElse(null);
        if (perm != null && perm.getNamespaceId() != null) {
            if (!permissionEvaluator.hasWritePermission(perm.getNamespaceId(), userId)) {
                throw new AccessDeniedException("Access denied: You do not have write permission on this namespace.");
            }
        }
    }

    @Before("execution(* io.github.matrixlane.metadataregistry.web.service.ItemAdministrationService.ensureAdministeredItem(..)) && args(item, req)")
    public void checkItemWritePermission(Item item, ItemRegistrationRequest req) {
        if (shouldBypass()) return;
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Access denied: Unauthenticated.");
        }

        // Check permission on existing namespace of the item (if any)
        UUID oldNamespaceId = getNamespaceIdFromItem(item);
        if (oldNamespaceId != null) {
            if (!permissionEvaluator.hasWritePermission(oldNamespaceId, userId)) {
                throw new AccessDeniedException("Access denied: You do not have write permission on the item's namespace.");
            }
        }

        // Check permission on new namespace being assigned (if different and not null)
        if (req != null && req.namespaceId() != null) {
            UUID newNamespaceId = req.namespaceId();
            if (!newNamespaceId.equals(oldNamespaceId)) {
                if (!permissionEvaluator.hasWritePermission(newNamespaceId, userId)) {
                    throw new AccessDeniedException("Access denied: You do not have write permission on the target namespace.");
                }
            }
        }
    }

    @Before("execution(* io.github.matrixlane.metadataregistry.web.service.*Service.delete(UUID)) && args(id)")
    public void checkItemDeletePermission(JoinPoint joinPoint, UUID id) {
        if (shouldBypass()) return;
        String serviceName = joinPoint.getTarget().getClass().getSimpleName();
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Access denied: Unauthenticated.");
        }

        if ("NamespaceService".equals(serviceName)) {
            // Namespace delete is handled by checkNamespaceDelete
            return;
        }
        if ("NamespacePermissionService".equals(serviceName)) {
            // Permission delete is handled by checkDeletePermission
            return;
        }

        // For other services, look up if the item belongs to a namespace
        List<ScopedIdentifier> sis = scopedIdentifierRepository.findByItemId(id);
        for (ScopedIdentifier si : sis) {
            if (si.getNamespace() != null) {
                UUID namespaceId = si.getNamespace().getId();
                if (!permissionEvaluator.hasWritePermission(namespaceId, userId)) {
                    throw new AccessDeniedException("Access denied: You do not have write permission on the item's namespace.");
                }
            }
        }
    }

    private UUID getNamespaceIdFromItem(Item item) {
        if (item == null) return null;
        IdentifiedItem ii = item.getIdentifiedItem();
        if (ii == null || ii.getScopedIdentifiers() == null || ii.getScopedIdentifiers().isEmpty()) {
            return null;
        }
        ScopedIdentifier si = ii.getScopedIdentifiers().get(0);
        if (si.getNamespace() != null) {
            return si.getNamespace().getId();
        }
        return null;
    }
}
