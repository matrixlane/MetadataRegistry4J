package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.language.LanguageRequest;
import io.github.matrixlane.metadataregistry.web.dto.language.LanguageResponse;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LanguageIdentificationService {

    private final LanguageIdentificationRepository repository;

    @Transactional(readOnly = true)
    public Page<LanguageResponse> list(String keyword, Boolean enabled, Pageable pageable) {
        Specification<LanguageIdentification> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("languageIdentifier")), like),
                    cb.like(cb.lower(root.get("scriptIdentifier")), like),
                    cb.like(cb.lower(root.get("geopoliticalTerritoryIdentifier")), like)));
        }
        if (enabled != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("enabled"), enabled));
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public LanguageResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public LanguageResponse create(LanguageRequest request) {
        if (repository.existsByLanguageIdentifier(request.languageIdentifier())) {
            throw new BusinessException("语言标识已存在：" + request.languageIdentifier());
        }
        LanguageIdentification entity = new LanguageIdentification();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public LanguageResponse update(UUID id, LanguageRequest request) {
        LanguageIdentification entity = loadOrThrow(id);
        if (!entity.getLanguageIdentifier().equalsIgnoreCase(request.languageIdentifier())
                && repository.existsByLanguageIdentifier(request.languageIdentifier())) {
            throw new BusinessException("语言标识已存在：" + request.languageIdentifier());
        }
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("LanguageIdentification", id);
        }
        repository.deleteById(id);
    }

    private LanguageIdentification loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LanguageIdentification", id));
    }

    private void applyRequest(LanguageIdentification entity, LanguageRequest request) {
        entity.setLanguageIdentifier(request.languageIdentifier());
        entity.setScriptIdentifier(request.scriptIdentifier());
        entity.setGeopoliticalTerritoryIdentifier(request.geopoliticalTerritoryIdentifier());
        entity.setPrivateUseQualifier(request.privateUseQualifier());
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        entity.getVariantIdentifier().clear();
        if (request.variantIdentifier() != null) {
            entity.getVariantIdentifier().addAll(request.variantIdentifier());
        }
        entity.getExtensionIdentifier().clear();
        if (request.extensionIdentifier() != null) {
            entity.getExtensionIdentifier().addAll(request.extensionIdentifier());
        }
    }

    private LanguageResponse toResponse(LanguageIdentification entity) {
        return new LanguageResponse(
                entity.getId(),
                entity.getLanguageIdentifier(),
                entity.getScriptIdentifier(),
                entity.getGeopoliticalTerritoryIdentifier(),
                new ArrayList<>(entity.getVariantIdentifier()),
                new ArrayList<>(entity.getExtensionIdentifier()),
                entity.getPrivateUseQualifier(),
                entity.getEnabled());
    }
}
