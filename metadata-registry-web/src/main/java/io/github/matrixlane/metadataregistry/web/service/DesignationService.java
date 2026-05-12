package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationContext;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationRequest;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationResponse;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConflictDetector;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionEngine;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionRuleResult;
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

        Sign sign = Sign.of(request.value());
        validateNamingConvention(sign);

        if (request.namespaceId() != null) {
            validateNoConflict(request.namespaceId(), sign, null);
        }

        if (request.acceptability() == Acceptability.PREFERRED) {
            validatePreferredUniqueness(item.getId(), request.languageId(), null);
        }

        Designation d = new Designation();
        d.setItem(item);
        d.setSign(sign);
        d.setLanguage(languageRepository.findById(request.languageId())
                .orElseThrow(() -> new ResourceNotFoundException("LanguageIdentification", request.languageId())));
        if (request.sourceId() != null) {
            d.setSource(referenceDocumentRepository.findById(request.sourceId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReferenceDocument", request.sourceId())));
        }
        if (request.namespaceId() != null) {
            Namespace ns = namespaceRepository.findById(request.namespaceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Namespace", request.namespaceId()));
            d.getNamespaces().add(ns);
        }
        if (request.contextId() != null) {
            Context ctx = contextRepository.findById(request.contextId())
                    .orElseThrow(() -> new ResourceNotFoundException("Context", request.contextId()));
            DesignationContext dc = new DesignationContext();
            dc.setDesignation(d);
            dc.setContext(ctx);
            dc.setAcceptability(request.acceptability());
            d.getDesignationContexts().add(dc);
        }

        return toResponse(designationRepository.save(d));
    }

    @Transactional
    public DesignationResponse update(UUID id, DesignationRequest request) {
        Designation d = loadOrThrow(id);

        Sign sign = Sign.of(request.value());
        validateNamingConvention(sign);

        if (request.namespaceId() != null) {
            validateNoConflict(request.namespaceId(), sign, d.getItem().getId());
        }

        if (request.acceptability() == Acceptability.PREFERRED) {
            validatePreferredUniqueness(d.getItem().getId(), request.languageId(), id);
        }

        d.setSign(sign);
        d.setLanguage(languageRepository.findById(request.languageId())
                .orElseThrow(() -> new ResourceNotFoundException("LanguageIdentification", request.languageId())));
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
        if (request.contextId() != null) {
            Context ctx = contextRepository.findById(request.contextId())
                    .orElseThrow(() -> new ResourceNotFoundException("Context", request.contextId()));
            d.getDesignationContexts().clear();
            DesignationContext dc = new DesignationContext();
            dc.setDesignation(d);
            dc.setContext(ctx);
            dc.setAcceptability(request.acceptability());
            d.getDesignationContexts().add(dc);
        }

        return toResponse(designationRepository.save(d));
    }

    @Transactional
    public void delete(UUID id) {
        if (!designationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Designation", id);
        }
        designationRepository.deleteById(id);
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

    private void validatePreferredUniqueness(UUID itemId, UUID languageId, UUID excludeDesignationId) {
        List<Designation> existing = designationRepository.findPreferredByItemIdAndLanguageId(itemId, languageId);
        boolean hasOther = existing.stream()
                .anyMatch(d -> excludeDesignationId == null || !d.getId().equals(excludeDesignationId));
        if (hasOther) {
            throw new BusinessException(ApiCodes.CONFLICT,
                    "同一 Item + 同一 Language 下只能有一个 PREFERRED 的 Designation");
        }
    }

    private DesignationResponse toResponse(Designation d) {
        UUID ctxId = null;
        String ctxLabel = null;
        String acceptability = null;
        if (d.getDesignationContexts() != null && !d.getDesignationContexts().isEmpty()) {
            DesignationContext dc = d.getDesignationContexts().get(0);
            if (dc.getContext() != null) {
                ctxId = dc.getContext().getId();
                ctxLabel = ItemNamingHelper.preferredDesignation(dc.getContext());
            }
            acceptability = dc.getAcceptability() == null ? null : dc.getAcceptability().name();
        }
        UUID nsId = null;
        String nsLabel = null;
        if (d.getNamespaces() != null && !d.getNamespaces().isEmpty()) {
            Namespace ns = d.getNamespaces().get(0);
            nsId = ns.getId();
            nsLabel = ItemNamingHelper.preferredDesignation(ns);
        }
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
                ctxId,
                ctxLabel,
                acceptability);
    }
}
