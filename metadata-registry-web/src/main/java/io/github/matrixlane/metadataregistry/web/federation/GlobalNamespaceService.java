package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierGenerationStrategyRegistry;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 全局命名空间与命名空间跨实例映射（registry-merge-strategy D3）。
 *
 * <p>每个注册表最多一个全局命名空间，专为数据迁移等跨实例"外交"任务存在，
 * 命名一眼可辨外部用途（"全局命名空间"），标识生成策略为 UUID，不关联注册
 * 机构。命名空间以 Administered_Item 包装（IdentifiedItem.item 指向命名空间）
 * 后在全局命名空间下挂接 Scoped_Identifier；来源方与目的方的命名空间副本持有
 * identifier 一致（同一 UUID）的 Scoped_Identifier，两侧命名空间凭该共享 UUID
 * 始终保持可匹配。多来源迁入复用同一个全局命名空间。
 */
@Service
@RequiredArgsConstructor
public class GlobalNamespaceService {

    /** 全局命名空间的 URN 方案引用（单例定位键）。 */
    public static final String GLOBAL_NAMESPACE_SCHEME = "urn:metadata-registry:global";

    /** 全局命名空间的标识前缀。 */
    public static final String GLOBAL_NAMESPACE_PREFIX = "glob:";

    /** 迁移生成的来源命名空间副本的方案引用前缀。 */
    public static final String IMPORTED_NAMESPACE_SCHEME_PREFIX = "urn:metadata-registry:imported:";

    private final NamespaceRepository namespaceRepository;
    private final AdministeredItemRepository administeredItemRepository;
    private final ScopedIdentifierRepository scopedIdentifierRepository;
    private final IdentifierStrategyConfigRepository identifierStrategyConfigRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final OrganizationRepository organizationRepository;

    public static boolean isGlobalNamespace(Namespace ns) {
        return ns != null && ns.getSchemeReference() != null
                && GLOBAL_NAMESPACE_SCHEME.equals(ns.getSchemeReference().getValue());
    }

    public static boolean isImportedNamespace(Namespace ns) {
        return ns != null && ns.getSchemeReference() != null
                && ns.getSchemeReference().getValue() != null
                && ns.getSchemeReference().getValue().startsWith(IMPORTED_NAMESPACE_SCHEME_PREFIX);
    }

    /**
     * 全局命名空间单例：首次迁移按需创建，之后一律复用，绝不新建第二个。
     */
    @Transactional
    public Namespace ensureGlobalNamespace() {
        return namespaceRepository.findBySchemeReference(GLOBAL_NAMESPACE_SCHEME)
                .orElseGet(this::createGlobalNamespace);
    }

    /**
     * 来源侧：为待迁移命名空间确保全局标识——以 Administered_Item 包装后在本方
     * 全局命名空间下持有 UUID 的 Scoped_Identifier；已有则复用。
     *
     * @return 全局标识值（UUID 字符串），即跨实例命名空间映射键
     */
    @Transactional
    public String ensureNamespaceGlobalIdentifier(Namespace contentNamespace) {
        Namespace global = ensureGlobalNamespace();
        Optional<ScopedIdentifier> existing = scopedIdentifierRepository
                .findByItemId(contentNamespace.getId()).stream()
                .filter(si -> si.getNamespace() != null && global.getId().equals(si.getNamespace().getId()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get().getIdentifier();
        }
        AdministeredItem wrapper = wrapperOf(contentNamespace);
        ScopedIdentifier si = new ScopedIdentifier(UUID.randomUUID().toString(), global, wrapper);
        si.setVersion("1");
        si.setCreatedAt(LocalDateTime.now());
        wrapper.getScopedIdentifiers().add(si);
        administeredItemRepository.save(wrapper);
        return si.getIdentifier();
    }

    /**
     * 目的侧：按共享 UUID 定位命名空间副本。
     */
    @Transactional(readOnly = true)
    public Optional<Namespace> findNamespaceByGlobalId(String globalId) {
        if (globalId == null || globalId.isBlank()) {
            return Optional.empty();
        }
        Namespace global = namespaceRepository.findBySchemeReference(GLOBAL_NAMESPACE_SCHEME).orElse(null);
        if (global == null) {
            return Optional.empty();
        }
        return scopedIdentifierRepository.findByNamespaceIdAndIdentifier(global.getId(), globalId)
                .map(si -> si.getIdentifiedItem() == null ? null : si.getIdentifiedItem().getItem())
                .filter(Namespace.class::isInstance)
                .map(Namespace.class::cast);
    }

    /**
     * 目的侧：确保来源命名空间副本存在——不存在则创建（不关联注册机构，
     * naming_authority 记录真实来源机构），并在同一个全局命名空间单例下建立
     * 与来源方 identifier 一致（同一 UUID）的 Scoped_Identifier。多来源迁入
     * 复用同一全局命名空间，各持不同 UUID 的标识。
     */
    @Transactional
    public Namespace ensureNamespaceCopy(String globalId, String sourceName, Organization namingAuthority) {
        String mappingId = (globalId == null || globalId.isBlank())
                ? UUID.randomUUID().toString() : globalId;
        Optional<Namespace> existing = findNamespaceByGlobalId(mappingId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Namespace copy = new Namespace();
        copy.setSchemeReference(Sign.of(IMPORTED_NAMESPACE_SCHEME_PREFIX + mappingId));
        copy.setNamingAuthority(namingAuthority);
        addDesignation(copy,
                (sourceName == null || sourceName.isBlank()) ? "imported-namespace" : sourceName,
                "zh-CN", "Hans", "CN");
        Namespace saved = namespaceRepository.save(copy);

        AdministeredItem wrapper = wrapperOf(saved);
        ScopedIdentifier si = new ScopedIdentifier(mappingId, ensureGlobalNamespace(), wrapper);
        si.setVersion("1");
        si.setCreatedAt(LocalDateTime.now());
        wrapper.getScopedIdentifiers().add(si);
        administeredItemRepository.save(wrapper);
        return saved;
    }

    /**
     * 解析命名空间副本的 naming_authority：优先按来源方记录的机构名匹配/创建
     * 组织；与 RAI 匹配的注册机构同名（或缺省）时直接使用该注册机构——RAI
     * 一致即同一机构在不同实例的表示。
     */
    @Transactional
    public Organization resolveNamingAuthority(String sourceNamingAuthorityName,
                                               Organization raiMatchedAuthority) {
        if (sourceNamingAuthorityName == null || sourceNamingAuthorityName.isBlank()) {
            return raiMatchedAuthority;
        }
        if (raiMatchedAuthority != null && sourceNamingAuthorityName.equals(raiMatchedAuthority.toString())) {
            return raiMatchedAuthority;
        }
        var matches = organizationRepository.findByNameValue(sourceNamingAuthorityName);
        if (!matches.isEmpty()) {
            return matches.get(0);
        }
        Organization org = new Organization();
        org.getName().add(Sign.of(sourceNamingAuthorityName));
        return organizationRepository.save(org);
    }

    private Namespace createGlobalNamespace() {
        Namespace ns = new Namespace();
        ns.setShorthandPrefix(GLOBAL_NAMESPACE_PREFIX);
        ns.setSchemeReference(Sign.of(GLOBAL_NAMESPACE_SCHEME));
        ns.setVersioningPolicy("SEQUENTIAL");
        addDesignation(ns, "global", "en-US", "Latn", "US");
        addDesignation(ns, "全局命名空间", "zh-CN", "Hans", "CN");
        Namespace saved = namespaceRepository.save(ns);

        IdentifierStrategyConfig config = new IdentifierStrategyConfig("UUID");
        config.setNamespace(saved);
        config.setIsGlobalDefault(false);
        config.publish();
        identifierStrategyConfigRepository.save(config);
        // 使策略立即生效（等价于 IdentifierStrategyLoader 启动时的装载）
        IdentifierGenerationStrategyRegistry.getInstance().setNamespaceConfig(saved.getId(), config);
        return saved;
    }

    /**
     * 命名空间作为被标识项：以 Administered_Item 包装（item 指向命名空间）。
     * 包装物为系统内部簿记对象，不关联注册机构。
     */
    private AdministeredItem wrapperOf(Namespace ns) {
        return administeredItemRepository.findByItem_Id(ns.getId()).orElseGet(() -> {
            AdministeredItem wrapper = new AdministeredItem(ns);
            RegistrationState state = new RegistrationState();
            state.setRegistrationStatus(RegistrationStatus.RECORDED);
            state.setEffectiveDate(LocalDateTime.now());
            wrapper.setRegistrationState(state);
            wrapper.setCreationDateTime(LocalDateTime.now());
            wrapper.setLastChangeDateTime(LocalDateTime.now());
            ns.setIdentifiedItem(wrapper);
            return wrapper;
        });
    }

    private void addDesignation(Namespace ns, String text, String langId, String script, String territory) {
        LanguageIdentification lang = languageRepository.findByLanguageIdentifier(langId)
                .orElseGet(() -> {
                    LanguageIdentification created = new LanguageIdentification();
                    created.setLanguageIdentifier(langId);
                    created.setScriptIdentifier(script);
                    created.setGeopoliticalTerritoryIdentifier(territory);
                    created.setEnabled(true);
                    return languageRepository.save(created);
                });
        Designation designation = new Designation();
        designation.setItem(ns);
        designation.setSign(Sign.of(text));
        designation.setLanguage(lang);
        ns.getDesignations().add(designation);
    }
}
