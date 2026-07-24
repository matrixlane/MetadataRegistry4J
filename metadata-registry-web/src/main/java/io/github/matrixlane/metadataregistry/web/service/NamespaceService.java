package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.NamespaceStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceDetailResponse;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceRequest;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceResponse;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceWizardRequest;
import io.github.matrixlane.metadataregistry.web.dto.namespace.DeletionCheckResponse;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.web.dto.namespace.ExternalNamespaceRequest;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NamespaceService {

    private final NamespaceRepository repository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final ItemAdministrationService itemAdministrationService;
    private final OrganizationRepository organizationRepository;

    /**
     * Create namespace using wizard request with full ISO 11179-3 attributes.
     * Validates namingAuthority requirement and namingConvention binding.
     *
     * @param request Wizard request with all governance attributes
     * @return Detailed namespace response
     * @throws BusinessException if validation fails
     */
    public NamespaceDetailResponse createWizard(NamespaceWizardRequest request) {
        validateNamingAuthorityRequired(request.namingAuthorityId());
        
        Namespace entity = new Namespace();
        
        // Apply naming authority
        if (request.namingAuthorityId() != null) {
            RegistrationAuthority ra = registrationAuthorityRepository.findById(request.namingAuthorityId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "RegistrationAuthority", request.namingAuthorityId()));
            entity.setRegistrationAuthority(ra);
            String raName = ra.getName().isEmpty() ? "" : ra.getName().get(0).getValue();
            entity.setNamingAuthority(raName);
        }
        
        // Apply parent hierarchy
        if (request.parentId() != null) {
            Namespace parent = repository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent Namespace", request.parentId()));
            entity.setParent(parent);
            inheritFromParent(entity, parent);
        }
        
        // Apply ISO governance attributes
        if (request.shorthandPrefix() != null) {
            entity.setShorthandPrefix(request.shorthandPrefix());
        }
        if (request.schemeReference() != null) {
            entity.setSchemeReference(request.schemeReference());
        }
        if (request.mandatoryNamingConventionIndicator() != null) {
            entity.setMandatoryNamingConventionIndicator(request.mandatoryNamingConventionIndicator());
            
            // Validate namingConvention binding when indicator is TRUE
            if (Boolean.TRUE.equals(request.mandatoryNamingConventionIndicator())) {
                validateNamingConventionRequired(request.namingConventionId());
                entity.setNamingConventionId(request.namingConventionId());
            }
        }
        if (request.oneNamePerItemIndicator() != null) {
            entity.setOneNamePerItemIndicator(request.oneNamePerItemIndicator());
        }
        if (request.oneItemPerNameIndicator() != null) {
            entity.setOneItemPerNameIndicator(request.oneItemPerNameIndicator());
        }
        
        // Apply designations and definitions
        itemAdministrationService.applyDesignations(entity, request.designations());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        
        // Apply registration metadata
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
        
        Namespace saved = repository.save(entity);
        return toDetailResponse(saved);
    }

    /**
     * Get namespace detail with all ISO 11179-3 attributes.
     */
    @Transactional(readOnly = true)
    public NamespaceDetailResponse getDetail(UUID id) {
        return toDetailResponse(loadOrThrow(id));
    }

    /**
     * Submit namespace for approval. Triggers WI-009 workflow.
     */
    public NamespaceDetailResponse submitForApproval(UUID id) {
        Namespace ns = loadOrThrow(id);
        if (ns.getNamespaceStatus() != NamespaceStatus.DRAFT) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "Namespace must be in DRAFT state to submit for approval");
        }
        ns.setNamespaceStatus(NamespaceStatus.SUBMITTED);
        Namespace saved = repository.save(ns);
        
        // TODO: Trigger WI-009 approval workflow via ApprovalService
        
        return toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<NamespaceResponse> list(String keyword, Pageable pageable) {
        Specification<Namespace> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> {
                if (q != null) q.distinct(true);
                var d = root.join("designations", jakarta.persistence.criteria.JoinType.LEFT);
                return cb.like(cb.lower(d.get("sign").get("value")), like);
            });
        }
        Page<Namespace> page = repository.findAll(spec, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NamespaceResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public NamespaceResponse create(NamespaceRequest request) {
        Namespace entity = new Namespace();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public NamespaceResponse update(UUID id, NamespaceRequest request) {
        Namespace entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Namespace", id);
        }
        repository.deleteById(id);
    }

    private Namespace loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Namespace", id));
    }

    private void applyRequest(Namespace entity, NamespaceRequest request) {
        if (entity.getId() != null && entity.getRegistrationAuthority() != null) {
            if (request.namingAuthorityId() == null || !request.namingAuthorityId().equals(entity.getRegistrationAuthority().getId())) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "Cannot modify registration authority for an internal namespace");
            }
        }

        if (request.namingAuthorityId() != null) {
            RegistrationAuthority ra = registrationAuthorityRepository.findById(request.namingAuthorityId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "RegistrationAuthority", request.namingAuthorityId()));
            entity.setRegistrationAuthority(ra);
            String raName = ra.getName().isEmpty() ? "" : ra.getName().get(0).getValue();
            entity.setNamingAuthority(raName);
        } else {
            entity.setRegistrationAuthority(null);
        }
        itemAdministrationService.applyDesignations(entity, request.designations());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
    }

    private NamespaceResponse toResponse(Namespace entity) {
        RegistrationAuthority ra = entity.getRegistrationAuthority();
        String raName = null;
        if (ra != null && !ra.getName().isEmpty()) {
            raName = ra.getName().get(0).getValue();
        }
        var des = itemAdministrationService.mapDesignations(entity);
        var def = itemAdministrationService.mapDefinitions(entity);
        var meta = itemAdministrationService.buildItemMeta(entity);
        String prefDes = ItemNamingHelper.preferredDesignation(entity);
        return new NamespaceResponse(
                entity.getId(),
                prefDes,
                ra == null ? null : ra.getId(),
                raName,
                des,
                def,
                meta);
    }

    private void validateNamingConventionRequired(UUID namingConventionId) {
        if (namingConventionId == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "NamingConvention is required when mandatoryNamingConventionIndicator = TRUE");
        }
    }

    private void inheritFromParent(Namespace child, Namespace parent) {
        // Child inherits namingAuthority if not set
        if (child.getNamingAuthority() == null && parent.getNamingAuthority() != null) {
            child.setNamingAuthority(parent.getNamingAuthority());
        }
        // Child inherits registrationAuthority if not set  
        if (child.getRegistrationAuthority() == null && parent.getRegistrationAuthority() != null) {
            child.setRegistrationAuthority(parent.getRegistrationAuthority());
        }
    }

    private int calculateDepth(Namespace ns) {
        int depth = 0;
        Namespace current = ns.getParent();
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    private NamespaceDetailResponse toDetailResponse(Namespace entity) {
        RegistrationAuthority ra = entity.getRegistrationAuthority();
        String raName = null;
        if (ra != null && !ra.getName().isEmpty()) {
            raName = ra.getName().get(0).getValue();
        }
        
        String parentLabel = null;
        if (entity.getParent() != null) {
            parentLabel = ItemNamingHelper.preferredDesignation(entity.getParent());
        }
        
        return new NamespaceDetailResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                ra == null ? null : ra.getId(),
                raName,
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity),
                entity.getShorthandPrefix(),
                entity.getSchemeReference(),
                entity.getMandatoryNamingConventionIndicator(),
                entity.getNamingConventionId(),
                entity.getOneNamePerItemIndicator(),
                entity.getOneItemPerNameIndicator(),
                entity.getNamespaceStatus(),
                entity.getParent() == null ? null : entity.getParent().getId(),
                parentLabel,
                calculateDepth(entity));
    }
    
    private void validateNamingAuthorityRequired(UUID namingAuthorityId) {
        if (namingAuthorityId == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "NamingAuthority is required when creating Namespace");
        }
    }

    @Transactional(readOnly = true)
    public DeletionCheckResponse checkDeletion(UUID id) {
        Namespace ns = loadOrThrow(id);
        int scopedIdentifierCount = ns.getScopedIdentifiers().size();
        boolean hasIdentifiers = scopedIdentifierCount > 0;
        return new DeletionCheckResponse(hasIdentifiers, scopedIdentifierCount, 
                "Namespace has " + scopedIdentifierCount + " associated scoped identifier(s).");
    }

    public NamespaceResponse createExternal(ExternalNamespaceRequest request) {
        Organization org = null;
        if (request.organizationId() != null) {
            org = organizationRepository.findById(request.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", request.organizationId()));
        } else if (request.newOrganizationName() != null && !request.newOrganizationName().isBlank()) {
            org = new Organization();
            org.getName().add(Sign.of(request.newOrganizationName()));
            org = organizationRepository.save(org);
        } else {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "必须提供已有组织ID或者新组织名称");
        }

        Namespace entity = new Namespace();
        entity.setRegistrationAuthority(null);
        entity.setNamingAuthority(org.getName().isEmpty() ? "" : org.getName().get(0).getValue());
        
        itemAdministrationService.applyDesignations(entity, request.designations());
        itemAdministrationService.applyDefinitions(entity, request.definitions());

        Namespace saved = repository.save(entity);
        return toResponse(saved);
    }
}
