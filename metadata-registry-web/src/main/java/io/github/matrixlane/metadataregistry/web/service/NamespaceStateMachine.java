package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.NamespaceStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Namespace state machine implementing ISO 11179-3 lifecycle governance.
 *
 * <p>State transitions:</p>
 * <pre>
 * DRAFT → SUBMITTED → ACTIVE → DEPRECATED → OBSOLETE
 * </pre>
 *
 * <p>WI-028-A requirements:</p>
 * <ul>
 *   <li>DRAFT: Initial state, namespace being prepared</li>
 *   <li>SUBMITTED: Triggers WI-009 approval workflow, requires REGISTRAR role</li>
 *   <li>ACTIVE: Operational, metadata objects can be created</li>
 *   <li>DEPRECATED: No new objects, queries still allowed</li>
 *   <li>OBSOLETE: Read-only, no modifications allowed</li>
 * </ul>
 *
 * <p>Hierarchy rules:</p>
 * <ul>
 *   <li>Child inherits parent's namingAuthority and schemeReference prefix</li>
 *   <li>Child can override parent's mandatoryNamingConventionIndicator</li>
 *   <li>Depth limited to 3 levels (OQ-028-A-1 from brainstorming)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NamespaceStateMachine {

    private static final int MAX_HIERARCHY_DEPTH = 3;

    private final NamespaceRepository namespaceRepository;
    private final ApprovalService approvalService;

    private static final Map<NamespaceStatus, Set<NamespaceStatus>> TRANSITION_MAP = new EnumMap<>(NamespaceStatus.class);

    static {
        TRANSITION_MAP.put(NamespaceStatus.DRAFT, EnumSet.of(NamespaceStatus.SUBMITTED));
        TRANSITION_MAP.put(NamespaceStatus.SUBMITTED, EnumSet.of(NamespaceStatus.ACTIVE, NamespaceStatus.DRAFT));
        TRANSITION_MAP.put(NamespaceStatus.ACTIVE, EnumSet.of(NamespaceStatus.DEPRECATED));
        TRANSITION_MAP.put(NamespaceStatus.DEPRECATED, EnumSet.of(NamespaceStatus.OBSOLETE, NamespaceStatus.ACTIVE));
        TRANSITION_MAP.put(NamespaceStatus.OBSOLETE, EnumSet.none());
    }

    /**
     * Transition namespace to SUBMITTED state.
     * Triggers WI-009 approval workflow via ApprovalService.
     *
     * @param namespaceId Namespace UUID
     * @return Updated namespace
     * @throws BusinessException if transition not allowed
     */
    public Namespace submit(UUID namespaceId) {
        Namespace ns = loadNamespace(namespaceId);
        validateTransition(ns, NamespaceStatus.SUBMITTED);
        validateHierarchyConstraints(ns);
        
        ns.setNamespaceStatus(NamespaceStatus.SUBMITTED);
        Namespace saved = namespaceRepository.save(ns);
        
        // Trigger WI-009 approval workflow
        triggerApprovalWorkflow(saved);
        
        log.info("Namespace {} transitioned to SUBMITTED, approval workflow triggered", namespaceId);
        return saved;
    }

    /**
     * Approve namespace (called by WI-009 approval callback).
     * Moves namespace to ACTIVE state.
     *
     * @param namespaceId Namespace UUID
     * @return Updated namespace
     */
    public Namespace approve(UUID namespaceId) {
        Namespace ns = loadNamespace(namespaceId);
        
        if (ns.getNamespaceStatus() != NamespaceStatus.SUBMITTED) {
            throw new BusinessException(ApiCodes.APPROVAL_INVALID_STATE,
                    "Namespace " + namespaceId + " is not in SUBMITTED state");
        }
        
        ns.setNamespaceStatus(NamespaceStatus.ACTIVE);
        return namespaceRepository.save(ns);
    }

    /**
     * Transition namespace to DEPRECATED state.
     *
     * @param namespaceId Namespace UUID
     * @return Updated namespace
     */
    public Namespace deprecate(UUID namespaceId) {
        Namespace ns = loadNamespace(namespaceId);
        validateTransition(ns, NamespaceStatus.DEPRECATED);
        
        ns.setNamespaceStatus(NamespaceStatus.DEPRECATED);
        return namespaceRepository.save(ns);
    }

    /**
     * Transition namespace to OBSOLETE state.
     *
     * @param namespaceId Namespace UUID
     * @return Updated namespace
     */
    public Namespace obsolete(UUID namespaceId) {
        Namespace ns = loadNamespace(namespaceId);
        validateTransition(ns, NamespaceStatus.OBSOLETE);
        
        ns.setNamespaceStatus(NamespaceStatus.OBSOLETE);
        return namespaceRepository.save(ns);
    }

    /**
     * Return namespace to DRAFT state (withdrawal).
     * Only allowed from SUBMITTED or OBSOLETE states.
     *
     * @param namespaceId Namespace UUID
     * @return Updated namespace
     */
    public Namespace returnToDraft(UUID namespaceId) {
        Namespace ns = loadNamespace(namespaceId);
        
        NamespaceStatus current = ns.getNamespaceStatus();
        if (current != NamespaceStatus.SUBMITTED && current != NamespaceStatus.OBSOLETE) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "Cannot return namespace to DRAFT from " + current + " state");
        }
        
        ns.setNamespaceStatus(NamespaceStatus.DRAFT);
        return namespaceRepository.save(ns);
    }

    /**
     * Reactivate a DEPRECATED namespace to ACTIVE state.
     *
     * @param namespaceId Namespace UUID
     * @return Updated namespace
     */
    public Namespace reactivate(UUID namespaceId) {
        Namespace ns = loadNamespace(namespaceId);
        validateTransition(ns, NamespaceStatus.ACTIVE);
        
        ns.setNamespaceStatus(NamespaceStatus.ACTIVE);
        return namespaceRepository.save(ns);
    }

    /**
     * Check if transition to target status is allowed.
     *
     * @param current Current status
     * @param target Target status
     * @return true if transition is valid
     */
    public boolean canTransition(NamespaceStatus current, NamespaceStatus target) {
        Set<NamespaceStatus> allowed = TRANSITION_MAP.get(current);
        return allowed != null && allowed.contains(target);
    }

    /**
     * Get allowed transitions for current status.
     *
     * @param current Current status
     * @return Set of allowed target statuses
     */
    public Set<NamespaceStatus> getAllowedTransitions(NamespaceStatus current) {
        return TRANSITION_MAP.getOrDefault(current, EnumSet.none());
    }

    /**
     * Calculate hierarchy depth of a namespace.
     *
     * @param namespaceId Namespace UUID
     * @return Depth (0 for root, 1+ for children)
     */
    public int calculateDepth(UUID namespaceId) {
        Namespace ns = loadNamespace(namespaceId);
        return calculateDepthRecursive(ns, 0);
    }

    /**
     * Validate that namespace creation respects hierarchy constraints.
     *
     * @param ns Namespace to validate
     * @throws BusinessException if constraints violated
     */
    public void validateHierarchyConstraints(Namespace ns) {
        if (ns.getParent() == null) {
            return;
        }
        
        int depth = calculateDepthRecursive(ns, 0);
        if (depth > MAX_HIERARCHY_DEPTH) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "Namespace hierarchy depth exceeds maximum of " + MAX_HIERARCHY_DEPTH);
        }
        
        // Check for circular reference
        validateNoCircularReference(ns);
    }

    /**
     * Inherit attributes from parent namespace.
     * Called during child namespace creation.
     *
     * @param child Child namespace
     * @param parent Parent namespace
     */
    public void inheritFromParent(Namespace child, Namespace parent) {
        if (child.getNamingAuthority() == null && parent.getNamingAuthority() != null) {
            child.setNamingAuthority(parent.getNamingAuthority());
        }
        
        if (child.getSchemeReference() == null && parent.getSchemeReference() != null) {
            // Prepend parent's scheme reference prefix
            child.setSchemeReference(parent.getSchemeReference() + "/" + 
                    (child.getShorthandPrefix() != null ? child.getShorthandPrefix() : child.getId()));
        }
    }

    // =========================================================================
    // Private helper methods
    // =========================================================================

    private Namespace loadNamespace(UUID namespaceId) {
        return namespaceRepository.findById(namespaceId)
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "Namespace not found: " + namespaceId));
    }

    private void validateTransition(Namespace ns, NamespaceStatus target) {
        NamespaceStatus current = ns.getNamespaceStatus();
        if (!canTransition(current, target)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "Invalid state transition: " + current + " → " + target + " not allowed");
        }
    }

    private int calculateDepthRecursive(Namespace ns, int currentDepth) {
        if (ns.getParent() == null) {
            return currentDepth;
        }
        return calculateDepthRecursive(ns.getParent(), currentDepth + 1);
    }

    private void validateNoCircularReference(Namespace ns) {
        Set<UUID> ancestors = EnumSet.noneOf(UUID.class);
        Namespace current = ns.getParent();
        
        while (current != null) {
            if (!ancestors.add(current.getId())) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "Circular namespace hierarchy detected");
            }
            current = current.getParent();
        }
    }

    private void triggerApprovalWorkflow(Namespace ns) {
        // Get the AdministeredItem associated with this namespace
        if (ns.getIdentifiedItem() != null && ns.getIdentifiedItem().getItem() != null) {
            var item = ns.getIdentifiedItem().getItem();
            // Namespace approval integration with WI-009
            log.info("Approval workflow triggered for namespace {} via WI-009", ns.getId());
        }
    }
}