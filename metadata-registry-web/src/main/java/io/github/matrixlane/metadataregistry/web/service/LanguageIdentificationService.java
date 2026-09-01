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
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LanguageIdentificationService {

    private final LanguageIdentificationRepository repository;
    private final LanguageGovernanceService governanceService;

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
            // enabled 为卫星扩展属性(缺失行等同启用):启用=不在显式禁用集合,禁用=在集合内
            java.util.Set<UUID> disabledIds = governanceService.disabledLanguageIds();
            spec = spec.and((root, q, cb) -> enabled
                    ? (disabledIds.isEmpty() ? cb.conjunction() : root.get("id").in(disabledIds).not())
                    : (disabledIds.isEmpty() ? cb.disjunction() : root.get("id").in(disabledIds)));
        }
        Page<LanguageIdentification> page = repository.findAll(spec, pageable);
        java.util.Map<UUID, io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance> governanceMap =
                governanceService.mapByLanguageIds(page.getContent().stream().map(LanguageIdentification::getId).toList());
        return page.map(entity -> toResponse(entity, governanceMap));
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
        LanguageIdentification saved = repository.save(entity);
        applyGovernance(saved.getId(), request.enabled());
        return toResponse(saved);
    }

    public LanguageResponse update(UUID id, LanguageRequest request) {
        LanguageIdentification entity = loadOrThrow(id);
        if (!entity.getLanguageIdentifier().equalsIgnoreCase(request.languageIdentifier())
                && repository.existsByLanguageIdentifier(request.languageIdentifier())) {
            throw new BusinessException("语言标识已存在：" + request.languageIdentifier());
        }
        applyRequest(entity, request);
        LanguageIdentification saved = repository.save(entity);
        applyGovernance(saved.getId(), request.enabled());
        return toResponse(saved);
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
        entity.getVariantIdentifier().clear();
        if (request.variantIdentifier() != null) {
            entity.getVariantIdentifier().addAll(request.variantIdentifier());
        }
        entity.getExtensionIdentifier().clear();
        if (request.extensionIdentifier() != null) {
            entity.getExtensionIdentifier().addAll(request.extensionIdentifier());
        }
    }

    private void applyGovernance(UUID languageId, Boolean enabled) {
        if (enabled != null) {
            governanceService.mutate(languageId, g -> g.setEnabled(enabled));
        }
    }

    private LanguageResponse toResponse(LanguageIdentification entity) {
        return toResponse(entity, java.util.Map.of());
    }

    private io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance governanceOf(
            LanguageIdentification entity,
            java.util.Map<UUID, io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance> governanceMap) {
        if (entity.getId() != null && governanceMap.containsKey(entity.getId())) {
            return governanceMap.get(entity.getId());
        }
        return governanceService.getOrDefault(entity.getId());
    }

    private LanguageResponse toResponse(LanguageIdentification entity,
            java.util.Map<UUID, io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance> governanceMap) {
        Locale displayLocale = LocaleContextHolder.getLocale();
        return new LanguageResponse(
                entity.getId(),
                entity.getLanguageIdentifier(),
                entity.getScriptIdentifier(),
                entity.getGeopoliticalTerritoryIdentifier(),
                LanguageDisplayNames.languageName(entity.getLanguageIdentifier(), displayLocale),
                LanguageDisplayNames.territoryName(entity.getGeopoliticalTerritoryIdentifier(), displayLocale),
                new ArrayList<>(entity.getVariantIdentifier()),
                new ArrayList<>(entity.getExtensionIdentifier()),
                entity.getPrivateUseQualifier(),
                governanceOf(entity, governanceMap).getEnabled());
    }
}
