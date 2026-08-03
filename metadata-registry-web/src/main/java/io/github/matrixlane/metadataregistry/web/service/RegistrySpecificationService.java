package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registries.RegistrySpecificationRequest;
import io.github.matrixlane.metadataregistry.web.dto.registries.RegistrySpecificationResponse;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RegistrySpecificationService {

    private final RegistrySpecificationRepository repository;
    private final ItemRepository itemRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final ContextRepository contextRepository;
    private final ItemAdministrationService itemAdministrationService;

    @Transactional(readOnly = true)
    public Page<RegistrySpecificationResponse> list(String keyword, Pageable pageable) {
        Specification<RegistrySpecification> spec = ObjectClassService.byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RegistrySpecificationResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

   public RegistrySpecificationResponse create(RegistrySpecificationRequest request) {
       RegistrySpecification entity = new RegistrySpecification();
       applyRequest(entity, request);
       return toResponse(repository.save(entity));
   }

   public RegistrySpecificationResponse update(UUID id, RegistrySpecificationRequest request) {
        RegistrySpecification entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

   public void delete(UUID id) {
       if (!repository.existsById(id)) {
           throw new ResourceNotFoundException("RegistrySpecification", id);
       }
       repository.deleteById(id);
   }

    @Transactional(readOnly = true)
    public boolean exists() {
        return repository.count() > 0;
    }

    /**
     * Clean up orphan registry_specification rows whose id no longer exists in the
     * inherited item table. This prevents Hibernate from failing to add the FK
     * constraint on startup. Idempotent: does nothing if no orphans are found.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void cleanupOrphanRecords() {
        long total = repository.count();
        if (total == 0) {
            return;
        }
        List<UUID> itemIds = itemRepository.findAll().stream()
                .map(Item::getId)
                .toList();
        List<RegistrySpecification> orphans = repository.findAll().stream()
                .filter(rs -> !itemIds.contains(rs.getId()))
                .toList();
        if (!orphans.isEmpty()) {
            log.warn("发现 {} 条 registry_specification 孤儿记录（在 item 表中无对应 id），即将清理", orphans.size());
            repository.deleteAll(orphans);
        }
    }

    /**
     * 创建默认注册规范。标识生成策略与版本生成策略属于命名空间范畴
     * （见 InitWizardService 的命名空间步骤），不由注册规范承载。
     */
    public RegistrySpecificationResponse createDefault(String registryName,
                                                     String defaultLanguage,
                                                     String description) {
        // 名称缺省值需在名称字段与首选指称中保持一致，否则 designation 为 null
        // 会触发命名规范校验失败。
        String effectiveName = registryName != null ? registryName : "默认元数据注册表";
        RegistrySpecificationRequest request = new RegistrySpecificationRequest(
                effectiveName,
                description,
                null,
                null,
                null,
                "UTF-8",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "UTC+8",
                List.of(new io.github.matrixlane.metadataregistry.web.dto.common.TextLine(defaultLanguageId(defaultLanguage), effectiveName, null, null, null, true)),
                null,
                null);
        return create(request);
    }

    private UUID defaultLanguageId(String languageIdentifier) {
        if (languageIdentifier == null || languageIdentifier.isBlank()) {
            return null;
        }
        return languageRepository.findByLanguageIdentifier(languageIdentifier)
                .map(LanguageIdentification::getId)
                .orElse(null);
    }

    private RegistrySpecification loadOrThrow(UUID id) {
       return repository.findById(id)
               .orElseThrow(() -> new ResourceNotFoundException("RegistrySpecification", id));
   }

    private void applyRequest(RegistrySpecification entity, RegistrySpecificationRequest request) {
        entity.setName(request.name());
        entity.setComment(request.comment());
        entity.setWebAddress(request.webAddress());
        entity.setCharacterRepertoire(request.characterRepertoire());
        entity.setRepresentationClassSchemeId(request.representationClassSchemeId());
        entity.setStandard(request.standard());
        entity.setDegreeOfConformance(request.degreeOfConformance());
        entity.setConformanceLevel(request.conformanceLevel());
        entity.setConformanceLabel(request.conformanceLabel());
        entity.setIncludedFeatures(request.includedFeatures());
        entity.setExcludedFeatures(request.excludedFeatures());
        entity.setDefaultTimeZone(request.defaultTimeZone());

        if (request.defaultLanguageId() != null) {
            LanguageIdentification lang = languageRepository.findById(request.defaultLanguageId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "LanguageIdentification", request.defaultLanguageId()));
            entity.setDefaultLanguage(lang);
        } else {
            entity.setDefaultLanguage(null);
        }

        entity.getAlternativeLanguages().clear();
        if (request.alternativeLanguageIds() != null) {
            for (UUID langId : request.alternativeLanguageIds()) {
                LanguageIdentification lang = languageRepository.findById(langId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "LanguageIdentification", langId));
                entity.getAlternativeLanguages().add(lang);
            }
        }

        entity.getReferenceDocumentIdentifierKinds().clear();
        if (request.referenceDocumentIdentifierKinds() != null) {
            entity.getReferenceDocumentIdentifierKinds().addAll(request.referenceDocumentIdentifierKinds());
        }

        if (request.contextId() != null) {
            Context ctx = contextRepository.findById(request.contextId())
                    .orElseThrow(() -> new ResourceNotFoundException("Context", request.contextId()));
            entity.setContext(ctx);
        } else {
            entity.setContext(null);
        }

        itemAdministrationService.applyDesignations(
                entity, request.designations(),
                request.registration() == null ? null : request.registration().namespaceId());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
    }

    private RegistrySpecificationResponse toResponse(RegistrySpecification entity) {
        LanguageIdentification defaultLang = entity.getDefaultLanguage();
        Context ctx = entity.getContext();
        List<UUID> altLangIds = new ArrayList<>();
        if (entity.getAlternativeLanguages() != null) {
            for (LanguageIdentification lang : entity.getAlternativeLanguages()) {
                altLangIds.add(lang.getId());
            }
        }
        // Copy the lazy @ElementCollection into a plain list inside the active
        // transaction; serializing the live PersistentCollection after the
        // session closes raises LazyInitializationException (HTTP 500).
        List<String> referenceDocumentIdentifierKinds =
                entity.getReferenceDocumentIdentifierKinds() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(entity.getReferenceDocumentIdentifierKinds());
        return new RegistrySpecificationResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getName(),
                entity.getComment(),
                entity.getWebAddress(),
                defaultLang == null ? null : defaultLang.getId(),
                defaultLang == null ? null : defaultLang.getLanguageIdentifier(),
                altLangIds,
                entity.getCharacterRepertoire(),
                referenceDocumentIdentifierKinds,
                entity.getRepresentationClassSchemeId(),
                ctx == null ? null : ctx.getId(),
                ctx == null ? null : ItemNamingHelper.preferredDesignation(ctx),
                entity.getStandard(),
                entity.getDegreeOfConformance(),
                entity.getConformanceLevel(),
                entity.getConformanceLabel(),
                entity.getIncludedFeatures(),
                entity.getExcludedFeatures(),
                entity.getDefaultTimeZone(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }
}
