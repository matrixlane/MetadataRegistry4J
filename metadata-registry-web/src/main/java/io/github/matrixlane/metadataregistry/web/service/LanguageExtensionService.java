package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.ext.basicandcore.LanguageIdentificationExtension;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationExtensionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LanguageIdentificationExtension 卫星实体(语言名称/地区名称)的读写服务。
 *
 * <p>缺失行语义:名称为纯展示字段,记录缺失或为空时返回 null(前端展示为空),
 * 本服务不生成默认名称行、不做运行时解析。1:1 单语设计:upsert 整行覆盖
 * 语言名称与地区名称。
 */
@Service
@RequiredArgsConstructor
public class LanguageExtensionService {

    private final LanguageIdentificationExtensionRepository extensionRepository;

    /** 按 language_id 取扩展行;无行或 id 为 null 时返回 null(名称展示为空)。 */
    @Transactional(readOnly = true)
    public LanguageIdentificationExtension getOrDefault(UUID languageId) {
        if (languageId == null) {
            return null;
        }
        return extensionRepository.findByLanguageId(languageId).orElse(null);
    }

    /** 批量取扩展行(列表页组装用,避免 N+1);空入参返回空 Map。 */
    @Transactional(readOnly = true)
    public Map<UUID, LanguageIdentificationExtension> mapByLanguageIds(Collection<UUID> languageIds) {
        if (languageIds == null || languageIds.isEmpty()) {
            return Map.of();
        }
        return extensionRepository.findByLanguageIdIn(languageIds).stream()
                .collect(Collectors.toMap(LanguageIdentificationExtension::getLanguageId, e -> e));
    }

    /** 按 language_id upsert:无行则新建,有行则覆盖名称(语言名称/地区名称成对写入)。 */
    @Transactional
    public LanguageIdentificationExtension upsert(UUID languageId, String languageName, String territoryName) {
        LanguageIdentificationExtension extension = extensionRepository.findByLanguageId(languageId)
                .orElseGet(() -> new LanguageIdentificationExtension(languageId));
        extension.setLanguageName(languageName);
        extension.setTerritoryName(territoryName);
        return extensionRepository.save(extension);
    }
}
