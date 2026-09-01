package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.DocumentType;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Notation;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.language.LanguageResponse;
import io.github.matrixlane.metadataregistry.web.dto.referencedocument.ReferenceDocumentRequest;
import io.github.matrixlane.metadataregistry.web.dto.referencedocument.ReferenceDocumentResponse;
import io.github.matrixlane.metadataregistry.web.repository.DocumentTypeRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ReferenceDocumentService {

    private final ReferenceDocumentRepository repository;
    private final DocumentTypeRepository documentTypeRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final LanguageGovernanceService governanceService;

    @Transactional(readOnly = true)
    public Page<ReferenceDocumentResponse> list(String keyword, Pageable pageable) {
        Specification<ReferenceDocument> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("identifier")), like),
                    cb.like(cb.lower(root.get("title").get("value")), like)));
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReferenceDocumentResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ReferenceDocumentResponse create(ReferenceDocumentRequest request) {
        if (repository.existsByIdentifier(request.identifier())) {
            throw new BusinessException("ReferenceDocument identifier 已存在：" + request.identifier());
        }
        ReferenceDocument entity = new ReferenceDocument();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public ReferenceDocumentResponse update(UUID id, ReferenceDocumentRequest request) {
        ReferenceDocument entity = loadOrThrow(id);
        if (!entity.getIdentifier().equals(request.identifier())
                && repository.existsByIdentifier(request.identifier())) {
            throw new BusinessException("ReferenceDocument identifier 已存在：" + request.identifier());
        }
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ReferenceDocument", id);
        }
        repository.deleteById(id);
    }

    private ReferenceDocument loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReferenceDocument", id));
    }

    private void applyRequest(ReferenceDocument entity, ReferenceDocumentRequest request) {
        entity.setIdentifier(request.identifier());
        entity.setTitle(request.title() == null ? null : Text.of(request.title()));
        entity.setNotation(request.notation() == null ? null : Notation.of(request.notation()));
        entity.setUri(request.uri());
        if (request.typeDescriptionId() != null) {
            DocumentType dt = documentTypeRepository.findById(request.typeDescriptionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "DocumentType", request.typeDescriptionId()));
            entity.setTypeDescription(dt);
        } else {
            entity.setTypeDescription(null);
        }
        entity.getLanguage().clear();
        if (request.languageIds() != null) {
            for (UUID langId : request.languageIds()) {
                LanguageIdentification li = languageRepository.findById(langId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "LanguageIdentification", langId));
                entity.getLanguage().add(li);
            }
        }
    }

    private ReferenceDocumentResponse toResponse(ReferenceDocument entity) {
        DocumentType type = entity.getTypeDescription();
        List<LanguageResponse> langs = new ArrayList<>();
        if (entity.getLanguage() != null) {
            Locale displayLocale = LocaleContextHolder.getLocale();
            for (LanguageIdentification li : entity.getLanguage()) {
                langs.add(new LanguageResponse(
                        li.getId(),
                        li.getLanguageIdentifier(),
                        li.getScriptIdentifier(),
                        li.getGeopoliticalTerritoryIdentifier(),
                        LanguageDisplayNames.languageName(li.getLanguageIdentifier(), displayLocale),
                        LanguageDisplayNames.territoryName(li.getGeopoliticalTerritoryIdentifier(), displayLocale),
                        new ArrayList<>(li.getVariantIdentifier()),
                        new ArrayList<>(li.getExtensionIdentifier()),
                        li.getPrivateUseQualifier(),
                        governanceService.getOrDefault(li.getId()).getEnabled()));
            }
        }
        return new ReferenceDocumentResponse(
                entity.getId(),
                entity.getIdentifier(),
                type == null ? null : type.getId(),
                type == null ? null : type.getIdentifier(),
                entity.getTitle() == null ? null : entity.getTitle().getValue(),
                entity.getNotation() == null ? null : entity.getNotation().getValue(),
                entity.getUri(),
                langs);
    }
}
