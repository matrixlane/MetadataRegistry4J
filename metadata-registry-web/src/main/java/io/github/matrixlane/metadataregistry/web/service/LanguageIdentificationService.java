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
    private final LanguageGovernanceService governanceService;
    private final LanguageExtensionService extensionService;

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
        java.util.List<UUID> languageIds = page.getContent().stream().map(LanguageIdentification::getId).toList();
        java.util.Map<UUID, io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance> governanceMap =
                governanceService.mapByLanguageIds(languageIds);
        java.util.Map<UUID, io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension> extensionMap =
                extensionService.mapByLanguageIds(languageIds);
        return page.map(entity -> toResponse(entity, governanceMap, extensionMap));
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
        applyExtensionNames(saved, request);
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
        applyExtensionNames(saved, request);
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

    /**
     * 语言名称/地区名称持久化:请求留空(空白/null)时存 null,
     * 不做运行时解析,名称数据仅可来自扩展表或 SQL 预置。
     */
    private void applyExtensionNames(LanguageIdentification entity, LanguageRequest request) {
        extensionService.upsert(entity.getId(),
                blankToNull(request.languageName()),
                blankToNull(request.territoryName()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private LanguageResponse toResponse(LanguageIdentification entity) {
        io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension extension =
                extensionService.getOrDefault(entity.getId());
        return toResponse(entity, java.util.Map.of(),
                extension == null ? java.util.Map.of() : java.util.Map.of(entity.getId(), extension));
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
            java.util.Map<UUID, io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageGovernance> governanceMap,
            java.util.Map<UUID, io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension> extensionMap) {
        io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension extension =
                entity.getId() == null ? null : extensionMap.get(entity.getId());
        return new LanguageResponse(
                entity.getId(),
                entity.getLanguageIdentifier(),
                entity.getScriptIdentifier(),
                entity.getGeopoliticalTerritoryIdentifier(),
                persistedName(extension == null ? null : extension.getLanguageName()),
                persistedName(extension == null ? null : extension.getTerritoryName()),
                new ArrayList<>(entity.getVariantIdentifier()),
                new ArrayList<>(entity.getExtensionIdentifier()),
                entity.getPrivateUseQualifier(),
                governanceOf(entity, governanceMap).getEnabled());
    }

    /** 名称仅取扩展表持久化值;记录缺失或名称为空白时返回 null(前端展示为空)。 */
    private static String persistedName(String name) {
        return name == null || name.isBlank() ? null : name;
    }
}
