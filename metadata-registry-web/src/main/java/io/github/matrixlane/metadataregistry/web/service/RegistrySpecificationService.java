package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.DegreeOfConformance;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
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
    private final LanguageIdentifierResolver languageIdentifierResolver;
    private final ContextRepository contextRepository;
    private final SystemSettingService systemSettingService;

    /**
     * 按名称过滤。注册规范是独立根实体（无指称），关键词只能匹配表 28 自有的
     * {@code name}（Sign）列；不能复用 {@code ObjectClassService.byKeyword}
     * ——其泛型上限为 {@code <T extends Item>}，且走 designation 子查询。
     */
    private Specification<RegistrySpecification> byKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Specification.where(null);
        }
        // %/_ 为 LIKE 通配符，按字面匹配需转义；小写化用 Locale.ROOT 避免土耳其语 I 问题
        String literal = keyword.toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String like = "%" + literal + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name").get("value")), like, '\\');
    }

    @Transactional(readOnly = true)
    public Page<RegistrySpecificationResponse> list(String keyword, Pageable pageable) {
        Specification<RegistrySpecification> spec = byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RegistrySpecificationResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

   /**
    * 新增注册规范。
    *
    * <p>注册规范为注册表的固有组成部分，全系统有且仅有一条：已存在记录时
    * MUST NOT 再次新增（400）。初始化向导经 {@link #createDefault} 创建前
    * 已清空业务数据（{@code InitWizardService.clearBusinessDataOnly}），
    * 不受此守卫影响。</p>
    */
   public RegistrySpecificationResponse create(RegistrySpecificationRequest request) {
       if (repository.count() > 0) {
           throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册规范有且仅有一条，不允许新增");
       }
       RegistrySpecification entity = new RegistrySpecification();
       applyRequest(entity, request);
       RegistrySpecification saved = repository.save(entity);
       return toResponse(saved);
   }

   public RegistrySpecificationResponse update(UUID id, RegistrySpecificationRequest request) {
        RegistrySpecification entity = loadOrThrow(id);
        applyRequest(entity, request);
        RegistrySpecification saved = repository.save(entity);
        return toResponse(saved);
    }

   /**
    * 删除注册规范。
    *
    * <p>系统初始化完成后，注册规范为注册表的固有组成部分（每个注册系统一条），
    * MUST NOT 删除（单条删除与批量删除均拒绝），仅允许编辑更新；未初始化
    * （向导草稿阶段）放行。</p>
    */
   public void delete(UUID id) {
       if (systemSettingService.isInitialized()) {
           throw new BusinessException(ApiCodes.VALIDATION_FAILED, "系统初始化后注册规范不允许删除，仅允许编辑");
       }
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
     * 创建默认注册规范（仅名称/语言/描述的兼容重载）。
     * 标识生成策略与版本生成策略属于命名空间范畴
     * （见 InitWizardService 的命名空间步骤），不由注册规范承载。
     */
   public RegistrySpecificationResponse createDefault(String registryName,
                                                    String defaultLanguage,
                                                    String description) {
        return createDefault(registryName, defaultLanguage, description,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * 创建默认注册规范：向导步骤可携带表 28 全量标量属性，缺省项按系统默认兜底
     * （字符集 UTF-8、标准 ISO/IEC 11179-3:2023、时区 +0800、默认语言 zh-CN）。
     * alternativeLanguageIdentifiers 使用语言标识符（如 en-US），在此解析为语言 id。
     */
   public RegistrySpecificationResponse createDefault(String registryName,
                                                    String defaultLanguage,
                                                    String description,
                                                    String webAddress,
                                                    String characterRepertoire,
                                                    String standard,
                                                    DegreeOfConformance degreeOfConformance,
                                                    String conformanceLevel,
                                                    String conformanceLabel,
                                                    String includedFeatures,
                                                    String excludedFeatures,
                                                    String defaultTimeZone,
                                                    List<String> alternativeLanguageIdentifiers,
                                                    List<String> referenceDocumentIdentifierKinds,
                                                    String representationClassSchemeId) {
        String effectiveName = registryName != null && !registryName.isBlank() ? registryName : "默认元数据注册表";
        String effectiveCharset = characterRepertoire != null && !characterRepertoire.isBlank()
                ? characterRepertoire : "UTF-8";
        String effectiveStandard = standard != null && !standard.isBlank() ? standard : "ISO/IEC 11179-3:2023";
        String effectiveTimeZone = defaultTimeZone != null && !defaultTimeZone.isBlank() ? defaultTimeZone : "+0800";
        UUID languageId = defaultLanguageId(defaultLanguage);
        List<UUID> altLangIds = alternativeLanguageIdentifiers == null
                ? java.util.List.of()
                : alternativeLanguageIdentifiers.stream()
                        .map(this::defaultLanguageId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();
        RegistrySpecificationRequest request = new RegistrySpecificationRequest(
                effectiveName,
                description,
                webAddress,
                languageId,
                altLangIds,
                effectiveCharset,
                referenceDocumentIdentifierKinds,
                representationClassSchemeId,
                null,
                effectiveStandard,
                degreeOfConformance,
                conformanceLevel,
                conformanceLabel,
                includedFeatures,
                excludedFeatures,
                effectiveTimeZone);
        return create(request);
    }

    private UUID defaultLanguageId(String languageIdentifier) {
        if (languageIdentifier == null || languageIdentifier.isBlank()) {
            return null;
        }
        return languageIdentifierResolver.resolve(languageIdentifier)
                .map(LanguageIdentification::getId)
                .orElse(null);
    }

    private RegistrySpecification loadOrThrow(UUID id) {
       return repository.findById(id)
               .orElseThrow(() -> new ResourceNotFoundException("RegistrySpecification", id));
   }

    private void applyRequest(RegistrySpecification entity, RegistrySpecificationRequest request) {
        entity.setName(Sign.of(request.name()));
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
        try {
            entity.setDefaultTimeZone(request.defaultTimeZone());
        } catch (IllegalArgumentException e) {
            // 实体按 ISO/IEC 11179-3:2023 表 28 校验 [-+]HHMM 格式；
            // 转换为业务校验异常，避免以 HTTP 500 冒泡。
            throw new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                    io.github.matrixlane.metadataregistry.web.common.ApiCodes.VALIDATION_FAILED, e.getMessage());
        }

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
                entity.getName() == null ? null : entity.getName().getValue(),
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
                entity.getDefaultTimeZone());
    }
}
