package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationContext;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationRequest;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationResponse;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConflictDetector;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionEngine;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionRuleResult;
import io.github.matrixlane.metadataregistry.web.service.namespace.NamespaceConstraintValidator;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Designation 独立 CRUD 服务。
 *
 * <p>集成命名规范校验、命名冲突检测、首选名称唯一性约束。
 * <p>支持多 DesignationContext 关联（Context + Acceptability），
 * 符合 ISO 11179-3 Clause 8.5.2 要求。
 */
@Service
@RequiredArgsConstructor
public class DesignationService {

    private final DesignationRepository designationRepository;
    private final ItemRepository itemRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final NamespaceRepository namespaceRepository;
    private final ContextRepository contextRepository;
    private final ReferenceDocumentRepository referenceDocumentRepository;
    private final NamingConventionEngine namingConventionEngine;
    private final NamingConflictDetector namingConflictDetector;
    private final NamespaceConstraintValidator namespaceConstraintValidator;
    private final NamingConventionRepository namingConventionRepository;
    private final RegistrySpecificationRepository registrySpecificationRepository;

    @Transactional(readOnly = true)
    public List<DesignationResponse> listByItemId(UUID itemId) {
        return designationRepository.findByItem_Id(itemId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DesignationResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional
    public DesignationResponse create(DesignationRequest request) {
        var item = itemRepository.findById(request.itemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", request.itemId()));

        LanguageIdentification language = resolveLanguage(request.languageId());
        Sign sign = Sign.of(request.value());
        validateNamingConvention(sign);

        if (request.namespaceId() != null) {
            validateNoConflict(request.namespaceId(), sign, null);
        }

        validatePreferredUniqueness(item.getId(), language.getId(), request.contexts(), null);

        Designation d = new Designation();
        d.setItem(item);
        d.setSign(sign);
        d.setLanguage(language);
        if (request.sourceId() != null) {
            d.setSource(referenceDocumentRepository.findById(request.sourceId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReferenceDocument", request.sourceId())));
        }
        if (request.namespaceId() != null) {
            Namespace ns = namespaceRepository.findById(request.namespaceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Namespace", request.namespaceId()));
            d.getNamespaces().add(ns);
        }
        applyNamingConventions(d, request.namingConventionIds());
        applyContexts(d, request.contexts());

        namespaceConstraintValidator.validateDesignation(request.namespaceId(), d, null);

        return toResponse(designationRepository.save(d));
    }

    @Transactional
    public DesignationResponse update(UUID id, DesignationRequest request) {
        Designation d = loadOrThrow(id);

        LanguageIdentification language = resolveLanguage(request.languageId());
        Sign sign = Sign.of(request.value());
        validateNamingConvention(sign);

        if (request.namespaceId() != null) {
            validateNoConflict(request.namespaceId(), sign, d.getItem().getId());
        }

        validatePreferredUniqueness(d.getItem().getId(), language.getId(), request.contexts(), id);

        d.setSign(sign);
        d.setLanguage(language);
        if (request.sourceId() != null) {
            d.setSource(referenceDocumentRepository.findById(request.sourceId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReferenceDocument", request.sourceId())));
        } else {
            d.setSource(null);
        }
        if (request.namespaceId() != null) {
            Namespace ns = namespaceRepository.findById(request.namespaceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Namespace", request.namespaceId()));
            d.getNamespaces().clear();
            d.getNamespaces().add(ns);
        }
        applyNamingConventions(d, request.namingConventionIds());
        // Clear and rebuild DesignationContexts
        d.getDesignationContexts().clear();
        applyContexts(d, request.contexts());

        namespaceConstraintValidator.validateDesignation(request.namespaceId(), d, d.getId());

        return toResponse(designationRepository.save(d));
    }

    @Transactional
    public void delete(UUID id) {
        if (!designationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Designation", id);
        }
        designationRepository.deleteById(id);
    }

    /**
     * Apply DesignationContext entries from the request to the Designation entity.
     */
    /**
     * 解析指称的语言。显式指定时直接加载；未指定时回退到注册表默认语言。
     * 若两者皆无，则抛出业务异常。
     */
    private LanguageIdentification resolveLanguage(UUID languageId) {
        if (languageId != null) {
            return languageRepository.findById(languageId)
                    .orElseThrow(() -> new ResourceNotFoundException("LanguageIdentification", languageId));
        }
        return loadDefaultLanguage()
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "未配置注册表默认语言，无法省略指称/定义的语言标识"));
    }

    private java.util.Optional<LanguageIdentification> loadDefaultLanguage() {
        return registrySpecificationRepository.findAll().stream()
                .findFirst()
                .map(rs -> rs.getDefaultLanguage());
    }

    private void applyContexts(Designation d, List<DesignationRequest.ContextEntry> contexts) {
        if (contexts == null || contexts.isEmpty()) return;
        for (DesignationRequest.ContextEntry entry : contexts) {
            Context ctx = contextRepository.findById(entry.contextId())
                    .orElseThrow(() -> new ResourceNotFoundException("Context", entry.contextId()));
            DesignationContext dc = new DesignationContext();
            dc.setDesignation(d);
            dc.setContext(ctx);
            dc.setAcceptability(entry.acceptability());
            d.getDesignationContexts().add(dc);
        }
    }

    private void applyNamingConventions(Designation d, List<UUID> ids) {
        d.getNamingConventions().clear();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<NamingConvention> conventions = namingConventionRepository.findAllById(ids);
        if (conventions.size() != ids.size()) {
            List<UUID> found = conventions.stream().map(NamingConvention::getId).toList();
            List<UUID> missing = ids.stream().filter(id -> !found.contains(id)).toList();
            throw new ResourceNotFoundException("NamingConvention", missing);
        }
        d.getNamingConventions().addAll(conventions);
    }

    private Designation loadOrThrow(UUID id) {
        return designationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Designation", id));
    }

    private void validateNamingConvention(Sign sign) {
        List<NamingConventionRuleResult> results = namingConventionEngine.validate(sign);
        List<String> failures = results.stream()
                .filter(r -> !r.isPassed())
                .map(r -> r.getRuleName() + ": " + String.join(", ", r.getMessages()))
                .toList();
        if (!failures.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "命名规范校验失败 [" + sign.getValue() + "]: " + String.join("; ", failures));
        }
    }

    private void validateNoConflict(UUID namespaceId, Sign sign, UUID excludeItemId) {
        if (namingConflictDetector.hasConflict(namespaceId, sign, excludeItemId)) {
            throw new BusinessException(ApiCodes.CONFLICT,
                    "命名冲突：Namespace 内已存在相同的 Sign 值 [" + sign.getValue() + "]");
        }
    }

    /**
     * Validate that within the given item + language, only one DesignationContext
     * has PREFERRED acceptability.
     *
     * @param itemId     the item the designation belongs to
     * @param languageId the language of the designation
     * @param contexts   the context entries from the request
     * @param excludeDesignationId optional designation id to exclude from check (on update)
     */
    private void validatePreferredUniqueness(UUID itemId, UUID languageId,
                                             List<DesignationRequest.ContextEntry> contexts,
                                             UUID excludeDesignationId) {
        // Check incoming request contexts
        boolean hasPreferredInRequest = false;
        if (contexts != null) {
            for (DesignationRequest.ContextEntry entry : contexts) {
                if (entry.acceptability() == Acceptability.PREFERRED) {
                    if (hasPreferredInRequest) {
                        throw new BusinessException(ApiCodes.CONFLICT,
                                "同一条指称下只能有一个 PREFERRED 的 DesignationContext");
                    }
                    hasPreferredInRequest = true;
                }
            }
        }

        // Check existing designations for PREFERRED in the same item + language
        if (hasPreferredInRequest) {
            List<Designation> existing = designationRepository.findPreferredByItemIdAndLanguageId(itemId, languageId);
            boolean hasOther = existing.stream()
                    .anyMatch(d -> excludeDesignationId == null || !d.getId().equals(excludeDesignationId));
            if (hasOther) {
                throw new BusinessException(ApiCodes.CONFLICT,
                        "同一 Item + 同一 Language 下只能有一个 PREFERRED 的 Designation");
            }
        }
    }

    private DesignationResponse toResponse(Designation d) {
        // Map multiple DesignationContexts
        List<DesignationResponse.ContextResponse> contextResponses = Collections.emptyList();
        if (d.getDesignationContexts() != null && !d.getDesignationContexts().isEmpty()) {
            contextResponses = d.getDesignationContexts().stream()
                    .map(dc -> new DesignationResponse.ContextResponse(
                            dc.getContext() == null ? null : dc.getContext().getId(),
                            dc.getContext() == null ? null : ItemNamingHelper.preferredDesignation(dc.getContext()),
                            dc.getAcceptability() == null ? null : dc.getAcceptability().name()))
                    .toList();
        }

        UUID nsId = null;
        String nsLabel = null;
        if (d.getNamespaces() != null && !d.getNamespaces().isEmpty()) {
            Namespace ns = d.getNamespaces().get(0);
                nsId = ns.getId();
                nsLabel = ItemNamingHelper.preferredDesignation(ns);
            }
            List<UUID> namingConventionIds = d.getNamingConventions() == null ? List.of()
                    : d.getNamingConventions().stream().map(NamingConvention::getId).toList();
            return new DesignationResponse(
                    d.getId(),
                d.getItem() == null ? null : d.getItem().getId(),
                d.getLanguage() == null ? null : d.getLanguage().getId(),
                d.getLanguage() == null ? null : d.getLanguage().getLanguageIdentifier(),
                d.getSign() == null ? null : d.getSign().getValue(),
                d.getSource() == null ? null : d.getSource().getId(),
                    d.getSource() == null || d.getSource().getTitle() == null
                            ? null : d.getSource().getTitle().getValue(),
                    nsId,
                    nsLabel,
                    namingConventionIds,
                    contextResponses);
        }
}
