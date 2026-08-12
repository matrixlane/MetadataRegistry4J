package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DefinitionContext;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DefinitionRequest;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DefinitionResponse;
import io.github.matrixlane.metadataregistry.web.entity.DefinitionAuditLog;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DefinitionRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Definition 独立 CRUD 服务。
 *
 * <p>集成定义操作日志。
 */
@Service
@RequiredArgsConstructor
public class DefinitionService {

    private final DefinitionRepository definitionRepository;
    private final ItemRepository itemRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final ContextRepository contextRepository;
    private final ReferenceDocumentRepository referenceDocumentRepository;
    private final DefinitionAuditLogService definitionAuditLogService;
    private final RegistrySpecificationRepository registrySpecificationRepository;

    @Transactional(readOnly = true)
    public List<DefinitionResponse> listByItemId(UUID itemId) {
        return definitionRepository.findByItem_Id(itemId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DefinitionResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional
    public DefinitionResponse create(DefinitionRequest request) {
        var item = itemRepository.findById(request.itemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", request.itemId()));

        LanguageIdentification language = resolveLanguage(request.languageId());
        Definition d = new Definition();
        d.setItem(item);
        d.setText(Text.of(request.value()));
        d.setLanguage(language);
        if (request.sourceId() != null) {
            d.setSource(referenceDocumentRepository.findById(request.sourceId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReferenceDocument", request.sourceId())));
        }
        if (request.contextId() != null) {
            Context ctx = contextRepository.findById(request.contextId())
                    .orElseThrow(() -> new ResourceNotFoundException("Context", request.contextId()));
            DefinitionContext dc = new DefinitionContext();
            dc.setDefinition(d);
            dc.setContext(ctx);
            dc.setAcceptability(request.acceptability());
            d.getDefinitionContexts().add(dc);
        }

        Definition saved = definitionRepository.save(d);

        definitionAuditLogService.save(DefinitionAuditLog.ofCreate(
                saved.getId(), item.getId(), request.value(),
                saved.getLanguage() == null ? null : saved.getLanguage().getLanguageIdentifier(),
                currentUser()));

        return toResponse(saved);
    }

    @Transactional
    public DefinitionResponse update(UUID id, DefinitionRequest request) {
        Definition d = loadOrThrow(id);
        String oldValue = d.getText() == null ? null : d.getText().getValue();
        String oldLang = d.getLanguage() == null ? null : d.getLanguage().getLanguageIdentifier();

        LanguageIdentification language = resolveLanguage(request.languageId());
        d.setText(Text.of(request.value()));
        d.setLanguage(language);
        if (request.sourceId() != null) {
            d.setSource(referenceDocumentRepository.findById(request.sourceId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReferenceDocument", request.sourceId())));
        } else {
            d.setSource(null);
        }
        if (request.contextId() != null) {
            Context ctx = contextRepository.findById(request.contextId())
                    .orElseThrow(() -> new ResourceNotFoundException("Context", request.contextId()));
            d.getDefinitionContexts().clear();
            DefinitionContext dc = new DefinitionContext();
            dc.setDefinition(d);
            dc.setContext(ctx);
            dc.setAcceptability(request.acceptability());
            d.getDefinitionContexts().add(dc);
        }

        Definition saved = definitionRepository.save(d);

        definitionAuditLogService.save(DefinitionAuditLog.ofUpdate(
                saved.getId(),
                saved.getItem() == null ? null : saved.getItem().getId(),
                oldValue, request.value(),
                saved.getLanguage() == null ? null : saved.getLanguage().getLanguageIdentifier(),
                currentUser()));

        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Definition d = loadOrThrow(id);
        UUID itemId = d.getItem() == null ? null : d.getItem().getId();
        String oldValue = d.getText() == null ? null : d.getText().getValue();
        String oldLang = d.getLanguage() == null ? null : d.getLanguage().getLanguageIdentifier();

        definitionRepository.deleteById(id);

        definitionAuditLogService.save(DefinitionAuditLog.ofDelete(
                id, itemId, oldValue, oldLang, currentUser()));
    }

    private Definition loadOrThrow(UUID id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Definition", id));
    }

    /**
     * 解析定义的语言。显式指定时直接加载；未指定时回退到注册表默认语言。
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

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "system";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser user) {
            return user.username();
        }
        return auth.getName();
    }

    private DefinitionResponse toResponse(Definition d) {
        List<DefinitionResponse.ContextResponse> contexts = Collections.emptyList();
        if (d.getDefinitionContexts() != null && !d.getDefinitionContexts().isEmpty()) {
            contexts = d.getDefinitionContexts().stream()
                    .map(dc -> new DefinitionResponse.ContextResponse(
                            dc.getContext() == null ? null : dc.getContext().getId(),
                            dc.getContext() == null ? null : ItemNamingHelper.preferredDesignation(dc.getContext()),
                            dc.getAcceptability() == null ? null : dc.getAcceptability().name()))
                    .toList();
        }
        return new DefinitionResponse(
                d.getId(),
                d.getItem() == null ? null : d.getItem().getId(),
                d.getLanguage() == null ? null : d.getLanguage().getId(),
                d.getLanguage() == null ? null : d.getLanguage().getLanguageIdentifier(),
                d.getText() == null ? null : d.getText().getValue(),
                d.getSource() == null ? null : d.getSource().getId(),
                d.getSource() == null || d.getSource().getTitle() == null
                        ? null : d.getSource().getTitle().getValue(),
                contexts);
    }
}
