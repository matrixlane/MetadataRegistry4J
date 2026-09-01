package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.ext.identification.NamespaceIdentifierSettings;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceIdentifierSettingsRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link NamespaceIdentifierSettings} 卫星实体读写服务。
 *
 * <p>读:{@link #getOrDefault(UUID)} 对缺失卫星行的既存数据返回默认值
 * (versioningPolicy=SEMVER、inputMode=AUTO_WITH_OVERRIDE、pattern=null),
 * 语义与字段在标准实体上时的列默认值一致。
 *
 * <p>写:Namespace 的创建路径均为服务层显式 save(ID 立即可得),
 * 故无需 ScopedIdentifierLifecycleService 的事务登记机制;新建与变更
 * 统一走 {@link #mutate(UUID, Consumer)} upsert。
 */
@Service
public class NamespaceIdentifierSettingsService {

    private final NamespaceIdentifierSettingsRepository repository;

    public NamespaceIdentifierSettingsService(NamespaceIdentifierSettingsRepository repository) {
        this.repository = repository;
    }

    /** 读取卫星行;缺失时返回未持久化的默认值实例(语义同字段时代的列默认值)。 */
    public NamespaceIdentifierSettings getOrDefault(UUID namespaceId) {
        if (namespaceId == null) {
            return new NamespaceIdentifierSettings();
        }
        return repository.findByNamespaceId(namespaceId)
                .orElseGet(NamespaceIdentifierSettings::new);
    }

    /** 批量读取,返回 namespaceId → 卫星行 映射(缺失的键不在 map 中)。 */
    public Map<UUID, NamespaceIdentifierSettings> mapByNamespaceIds(Collection<UUID> namespaceIds) {
        if (namespaceIds == null || namespaceIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByNamespaceIdIn(namespaceIds).stream()
                .collect(Collectors.toMap(NamespaceIdentifierSettings::getNamespaceId,
                        Function.identity()));
    }

    public NamespaceIdentifierSettings save(NamespaceIdentifierSettings settings) {
        return repository.save(settings);
    }

    /** 新建/变更路径:加载(或新建)卫星行,应用变更后落库。 */
    public NamespaceIdentifierSettings mutate(UUID namespaceId,
                                              Consumer<NamespaceIdentifierSettings> mutator) {
        NamespaceIdentifierSettings settings = getOrDefault(namespaceId);
        mutator.accept(settings);
        if (settings.getNamespaceId() == null) {
            settings.setNamespaceId(namespaceId);
        }
        return repository.save(settings);
    }
}
