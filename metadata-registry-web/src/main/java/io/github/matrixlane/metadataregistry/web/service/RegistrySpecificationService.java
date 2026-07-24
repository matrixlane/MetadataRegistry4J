package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registries.RegistrySpecificationRequest;
import io.github.matrixlane.metadataregistry.web.dto.registries.RegistrySpecificationResponse;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RegistrySpecificationService {

    private final RegistrySpecificationRepository repository;
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

        itemAdministrationService.applyDesignations(entity, request.designations());
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
                entity.getReferenceDocumentIdentifierKinds(),
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
