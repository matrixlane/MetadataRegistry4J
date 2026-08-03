package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceDetailResponse;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceRequest;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceResponse;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceWizardRequest;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.service.namespace.NamespaceConstraintValidator;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import io.github.matrixlane.metadataregistry.web.dto.namespace.DeletionCheckResponse;
import io.github.matrixlane.metadataregistry.web.dto.namespace.ExternalNamespaceRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NamespaceService {

    private final NamespaceRepository repository;
    private final OrganizationRepository organizationRepository;
    private final NamingConventionRepository namingConventionRepository;
    private final ItemAdministrationService itemAdministrationService;
    private final NamespaceConstraintValidator namespaceConstraintValidator;

    /**
     * Create namespace using wizard request with full ISO 11179-3 attributes.
     *
     * @param request Wizard request with all governance attributes
     * @return Detailed namespace response
     * @throws BusinessException if validation fails
     */
    public NamespaceDetailResponse createWizard(NamespaceWizardRequest request) {
        Namespace entity = new Namespace();
        applyWizardRequest(entity, request);
        namespaceConstraintValidator.validateNamespace(entity);
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
     * 分页列出命名空间。
     */
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
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NamespaceResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public NamespaceResponse create(NamespaceRequest request) {
        Namespace entity = new Namespace();
        applyRequest(entity, request);
        namespaceConstraintValidator.validateNamespace(entity);
        return toResponse(repository.save(entity));
    }

    public NamespaceResponse update(UUID id, NamespaceRequest request) {
        Namespace entity = loadOrThrow(id);
        applyRequest(entity, request);
        namespaceConstraintValidator.validateNamespace(entity);
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
        if (request.namingAuthorityId() != null) {
            Organization org = organizationRepository.findById(request.namingAuthorityId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Organization", request.namingAuthorityId()));
            entity.setNamingAuthority(org);
        } else {
            entity.setNamingAuthority(null);
        }

        if (request.shorthandPrefix() != null) {
            entity.setShorthandPrefix(request.shorthandPrefix());
        }
        if (request.schemeReference() != null) {
            entity.setSchemeReference(request.schemeReference());
        }
        if (request.mandatoryNamingConventionIndicator() != null) {
            entity.setMandatoryNamingConventionIndicator(request.mandatoryNamingConventionIndicator());
        }
        applyNamingConventions(entity, request.namingConventionIds());
        if (request.oneNamePerItemIndicator() != null) {
            entity.setOneNamePerItemIndicator(request.oneNamePerItemIndicator());
        }
        if (request.oneItemPerNameIndicator() != null) {
            entity.setOneItemPerNameIndicator(request.oneItemPerNameIndicator());
        }

        itemAdministrationService.applyDesignations(entity, request.designations());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
    }

    private void applyWizardRequest(Namespace entity, NamespaceWizardRequest request) {
        if (request.namingAuthorityId() != null) {
            Organization org = organizationRepository.findById(request.namingAuthorityId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Organization", request.namingAuthorityId()));
            entity.setNamingAuthority(org);
        }

        if (request.shorthandPrefix() != null) {
            entity.setShorthandPrefix(request.shorthandPrefix());
        }
        if (request.schemeReference() != null) {
            entity.setSchemeReference(request.schemeReference());
        }
        if (request.mandatoryNamingConventionIndicator() != null) {
            entity.setMandatoryNamingConventionIndicator(request.mandatoryNamingConventionIndicator());
        }
        if (request.namingConventionId() != null) {
            applyNamingConventions(entity, List.of(request.namingConventionId()));
        } else {
            applyNamingConventions(entity, null);
        }
        if (request.oneNamePerItemIndicator() != null) {
            entity.setOneNamePerItemIndicator(request.oneNamePerItemIndicator());
        }
        if (request.oneItemPerNameIndicator() != null) {
            entity.setOneItemPerNameIndicator(request.oneItemPerNameIndicator());
        }

        itemAdministrationService.applyDesignations(entity, request.designations());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
    }

    private void applyNamingConventions(Namespace entity, List<UUID> ids) {
        if (ids == null) {
            return;
        }
        entity.getNamingConventions().clear();
        if (ids.isEmpty()) {
            return;
        }
        List<NamingConvention> conventions = namingConventionRepository.findAllById(ids);
        if (conventions.size() != ids.size()) {
            List<UUID> found = conventions.stream().map(NamingConvention::getId).toList();
            List<UUID> missing = ids.stream().filter(id -> !found.contains(id)).toList();
            throw new ResourceNotFoundException("NamingConvention", missing);
        }
        entity.getNamingConventions().addAll(conventions);
    }

    private NamespaceResponse toResponse(Namespace entity) {
        Organization namingAuthority = entity.getNamingAuthority();
        String namingAuthorityName = namingAuthority == null || namingAuthority.getName().isEmpty()
                ? null : namingAuthority.getName().get(0).getValue();
        List<UUID> namingConventionIds = entity.getNamingConventions().stream()
                .map(NamingConvention::getId)
                .toList();
        return new NamespaceResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                namingAuthority == null ? null : namingAuthority.getId(),
                namingAuthorityName,
                entity.getRegistrationAuthority() == null ? null : entity.getRegistrationAuthority().getId(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                entity.getShorthandPrefix(),
                entity.getSchemeReference(),
                entity.getMandatoryNamingConventionIndicator(),
                namingConventionIds,
                entity.getOneNamePerItemIndicator(),
                entity.getOneItemPerNameIndicator());
    }

    private NamespaceDetailResponse toDetailResponse(Namespace entity) {
        Organization namingAuthority = entity.getNamingAuthority();
        String namingAuthorityName = namingAuthority == null || namingAuthority.getName().isEmpty()
                ? null : namingAuthority.getName().get(0).getValue();
        List<UUID> namingConventionIds = entity.getNamingConventions().stream()
                .map(NamingConvention::getId)
                .toList();

        return new NamespaceDetailResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                namingAuthority == null ? null : namingAuthority.getId(),
                namingAuthorityName,
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                entity.getShorthandPrefix(),
                entity.getSchemeReference(),
                entity.getMandatoryNamingConventionIndicator(),
                namingConventionIds,
                entity.getOneNamePerItemIndicator(),
                entity.getOneItemPerNameIndicator());
    }

    private void validateNamingAuthorityRequired(UUID namingAuthorityId) {
        if (namingAuthorityId == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "NamingAuthority is required when creating Namespace");
        }
    }public DeletionCheckResponse checkDeletion(UUID id) {
        Namespace ns = loadOrThrow(id);
        int scopedIdentifierCount = ns.getScopedIdentifiers().size();
        boolean hasIdentifiers = scopedIdentifierCount > 0;
        return new DeletionCheckResponse(hasIdentifiers, scopedIdentifierCount,
                "Namespace has " + scopedIdentifierCount + " associated scoped identifier(s).");
    }public NamespaceResponse createExternal(ExternalNamespaceRequest request) {
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
        entity.setNamingAuthority(org);

        itemAdministrationService.applyDesignations(entity, request.designations());
        itemAdministrationService.applyDefinitions(entity, request.definitions());

        Namespace saved = repository.save(entity);
        return toResponse(saved);
    }
}

