package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceDetailResponse;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceRequest;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceResponse;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceWizardRequest;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.service.identifier.NamespaceIdentifierPolicy;
import io.github.matrixlane.metadataregistry.web.service.identifier.NamespaceIdentifierPolicyResolver;
import io.github.matrixlane.metadataregistry.web.service.namespace.NamespaceConstraintValidator;
import io.github.matrixlane.metadataregistry.web.service.namespace.NamespaceOwnershipService;
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
    private final NamespaceIdentifierPolicyResolver policyResolver;
    private final IdentifierStrategyConfigRepository strategyConfigRepository;
    private final IdentifierStrategyLoader strategyLoader;
    private final NamespaceOwnershipService namespaceOwnershipService;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

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
        Namespace saved = repository.save(entity);
        applyStrategyConfig(saved, request);
        return toResponse(saved);
    }

    public NamespaceResponse update(UUID id, NamespaceRequest request) {
        Namespace entity = loadOrThrow(id);
        applyRequest(entity, request);
        namespaceConstraintValidator.validateNamespace(entity);
        Namespace saved = repository.save(entity);
        applyStrategyConfig(saved, request);
        return toResponse(saved);
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
            entity.setSchemeReference(Sign.of(request.schemeReference()));
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
        // Only touch policy fields when the caller supplies them; requests
        // omitting them leave existing settings untouched.
        if (request.identifierInputMode() != null) {
            entity.setIdentifierInputMode(request.identifierInputMode());
        }
        if (request.versionInputMode() != null) {
            entity.setVersionInputMode(request.versionInputMode());
        }
        if (request.versioningPolicy() != null) {
            entity.setVersioningPolicy(request.versioningPolicy());
        }
        if (request.manualIdentifierPattern() != null) {
            entity.setManualIdentifierPattern(request.manualIdentifierPattern());
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
            entity.setSchemeReference(Sign.of(request.schemeReference()));
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

    /**
     * Upserts the namespace's identifier generation strategy config
     * (UUID / SEQUENCE / TEMPLATE). Only invoked when the request carries the
     * {@code identifierStrategy} field; omitted requests leave existing
     * configs untouched. Publishes a new version row, disables previous
     * PUBLISHED versions, and refreshes the in-memory strategy registry.
     */
    private void applyStrategyConfig(Namespace entity, NamespaceRequest request) {
        String type = request.identifierStrategy();
        if (type == null) {
            return;
        }
        List<IdentifierStrategyConfig> existing =
                strategyConfigRepository.findByNamespaceIdOrderByVersionDesc(entity.getId());
        for (IdentifierStrategyConfig c : existing) {
            if (c.getStatus() == IdentifierStrategyConfig.StrategyStatus.PUBLISHED) {
                c.disable();
                strategyConfigRepository.save(c);
            }
        }
        if (!type.isBlank() && !"UUID".equalsIgnoreCase(type)) {
            int nextVersion = existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;
            IdentifierStrategyConfig config = new IdentifierStrategyConfig(type.toUpperCase());
            config.setNamespace(entity);
            config.setVersion(nextVersion);
            config.setConfigJson(buildConfigJson(type.toUpperCase(), request));
            config.setCreatedAt(java.time.LocalDateTime.now());
            config.setCreatedBy("namespace-settings");
            config.setIsGlobalDefault(false);
            config.publish();
            strategyConfigRepository.save(config);
        }
        strategyLoader.refreshRegistry();
    }

    private String buildConfigJson(String type, NamespaceRequest request) {
        try {
            if ("SEQUENCE".equals(type)) {
                return JSON.writeValueAsString(java.util.Map.of(
                        "prefix", request.strategyPrefix() != null ? request.strategyPrefix() : "",
                        "start", request.strategySeqStart() != null ? request.strategySeqStart() : 1L,
                        "incrementBy", 1,
                        "pad", request.strategySeqPad() != null ? request.strategySeqPad() : 6));
            }
            if ("TEMPLATE".equals(type)) {
                return JSON.writeValueAsString(java.util.Map.of(
                        "template", request.strategyTemplate() != null ? request.strategyTemplate() : "{prefix}-{seq:4}",
                        "prefix", request.strategyPrefix() != null ? request.strategyPrefix() : "",
                        "pad", request.strategySeqPad() != null ? request.strategySeqPad() : 4));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "策略配置序列化失败: " + e.getMessage());
        }
        return "{}";
    }

    /**
     * Reads the namespace's currently effective strategy config for response
     * mapping. Returns strategy type UUID with null params when none exists
     * (built-in UUID fallback).
     */
    private StrategyConfigView strategyView(Namespace entity) {
        List<IdentifierStrategyConfig> configs =
                strategyConfigRepository.findByNamespaceIdOrderByVersionDesc(entity.getId());
        IdentifierStrategyConfig active = configs.stream()
                .filter(IdentifierStrategyConfig::isEffective)
                .findFirst()
                .orElse(null);
        if (active == null) {
            return new StrategyConfigView("UUID", null, null, null, null);
        }
        String prefix = null;
        String template = null;
        Long start = null;
        Integer pad = null;
        try {
            if (active.getConfigJson() != null && !active.getConfigJson().isBlank()) {
                var map = JSON.readValue(active.getConfigJson(), java.util.Map.class);
                prefix = (String) map.get("prefix");
                template = (String) map.get("template");
                if (map.get("start") instanceof Number n) {
                    start = n.longValue();
                }
                if (map.get("pad") instanceof Number n) {
                    pad = n.intValue();
                }
            }
        } catch (Exception e) {
            // Tolerate dirty config JSON: expose type only
        }
        return new StrategyConfigView(active.getStrategyType(), prefix, template, start, pad);
    }

    private record StrategyConfigView(String type, String prefix, String template,
                                      Long start, Integer pad) {
    }

    private NamespaceResponse toResponse(Namespace entity) {
        Organization namingAuthority = entity.getNamingAuthority();
        String namingAuthorityName = namingAuthority == null || namingAuthority.getName().isEmpty()
                ? null : namingAuthority.getName().get(0).getValue();
        List<UUID> namingConventionIds = entity.getNamingConventions().stream()
                .map(NamingConvention::getId)
                .toList();
        NamespaceIdentifierPolicy policy = policyResolver.resolve(entity);
        StrategyConfigView strategy = strategyView(entity);
        return new NamespaceResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                namingAuthority == null ? null : namingAuthority.getId(),
                namingAuthorityName,
                entity.getRegistrationAuthority() == null ? null : entity.getRegistrationAuthority().getId(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                entity.getShorthandPrefix(),
                entity.getSchemeReference() == null ? null : entity.getSchemeReference().getValue(),
                entity.getMandatoryNamingConventionIndicator(),
                namingConventionIds,
                entity.getOneNamePerItemIndicator(),
                entity.getOneItemPerNameIndicator(),
                policy.inputMode().name(),
                policy.versionInputMode().name(),
                policy.versioningPolicy().name(),
                policy.manualIdentifierPattern(),
                strategy.type(),
                strategy.prefix(),
                strategy.template(),
                strategy.start(),
                strategy.pad(),
                namespaceKind(entity));
    }

    private NamespaceDetailResponse toDetailResponse(Namespace entity) {
        Organization namingAuthority = entity.getNamingAuthority();
        String namingAuthorityName = namingAuthority == null || namingAuthority.getName().isEmpty()
                ? null : namingAuthority.getName().get(0).getValue();
        List<UUID> namingConventionIds = entity.getNamingConventions().stream()
                .map(NamingConvention::getId)
                .toList();
        NamespaceIdentifierPolicy policy = policyResolver.resolve(entity);
        StrategyConfigView strategy = strategyView(entity);

        return new NamespaceDetailResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                namingAuthority == null ? null : namingAuthority.getId(),
                namingAuthorityName,
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                entity.getShorthandPrefix(),
                entity.getSchemeReference() == null ? null : entity.getSchemeReference().getValue(),
                entity.getMandatoryNamingConventionIndicator(),
                namingConventionIds,
                entity.getOneNamePerItemIndicator(),
                entity.getOneItemPerNameIndicator(),
                policy.inputMode().name(),
                policy.versionInputMode().name(),
                policy.versioningPolicy().name(),
                policy.manualIdentifierPattern(),
                strategy.type(),
                strategy.prefix(),
                strategy.template(),
                strategy.start(),
                strategy.pad(),
                namespaceKind(entity));
    }

    /** 自有/外部判定（D8 临时判定；active-registrar-ownership 就绪后切换）。 */
    private String namespaceKind(Namespace entity) {
        return namespaceOwnershipService.isOwn(entity) ? "OWN" : "EXTERNAL";
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

