package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfigService;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierVersionHistory;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.ext.identification.Versions;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.Property;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierVersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.service.ItemAdministrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 按快照中的 itemType 创建对应 Item 子类，并复用通用注册管线
 * （designations/definitions/AdministeredItem/ScopedIdentifier/RegistrationState）。
 *
 * <p>类型特有引用（如数据元素的数据类型引用）属于实例内部关联，跨实例不可映射，
 * 本期创建时留空，由目标端用户后续补全。
 */
@Component
@RequiredArgsConstructor
public class GenericItemCreator {

    private static final Map<String, Supplier<? extends Item>> TYPE_REGISTRY = Map.ofEntries(
            Map.entry("DataElement", DataElement::new),
            Map.entry("ObjectClass", ObjectClass::new),
            Map.entry("Property", Property::new),
            // 导出端 itemType 取 getClass().getSimpleName()(具体子类名),原先的
            // "ConceptualDomain"/"ValueDomain" 基类键永远匹配不上;且基类已按
            // 11179-31 修正为 abstract,此处注册全部具体子类。
            Map.entry("DescribedConceptualDomain", DescribedConceptualDomain::new),
            Map.entry("LocalEnumeratedConceptualDomain", LocalEnumeratedConceptualDomain::new),
            Map.entry("ReferenceEnumeratedConceptualDomain", ReferenceEnumeratedConceptualDomain::new),
            Map.entry("DescribedValueDomain", DescribedValueDomain::new),
            Map.entry("LocalEnumeratedValueDomain", LocalEnumeratedValueDomain::new),
            Map.entry("ReferenceEnumeratedValueDomain", ReferenceEnumeratedValueDomain::new),
            Map.entry("DataElementConcept", DataElementConcept::new),
            Map.entry("DataType", DataType::new),
            Map.entry("Context", Context::new)
    );

    private final ItemAdministrationService itemAdministrationService;
    private final ItemRepository itemRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final NamespaceRepository namespaceRepository;
    private final ScopedIdentifierRepository scopedIdentifierRepository;
    private final IdentifierVersionHistoryRepository identifierVersionHistoryRepository;
    private final IdentifierStrategyConfigService identifierStrategyConfigService;
    private final io.github.matrixlane.metadataregistry.web.service.ScopedIdentifierLifecycleService lifecycleService;
    private final io.github.matrixlane.metadataregistry.web.service.NamespaceIdentifierSettingsService settingsService;
    private final GlobalNamespaceService globalNamespaceService;

    public AdministeredItem create(BundleItemSnapshot snapshot, UUID targetNamespaceId, RegistryNode node) {
        String itemType = snapshot.export() == null ? null : snapshot.export().itemType();
        Supplier<? extends Item> constructor = TYPE_REGISTRY.get(itemType);
        if (constructor == null) {
            throw new BusinessException("暂不支持的条目类型: " + itemType
                    + "（支持: " + TYPE_REGISTRY.keySet() + "）");
        }
        Item item = constructor.get();
        applyImportValidPeriodFallback(item);

        // 归属重关联（registry-merge-strategy D2）：按 RAI 匹配目的方注册机构，
        // 替代随意取本地第一个机构的旧行为
        RegistrationAuthority ra = registrationAuthorityRepository.findByRai(node.getRai())
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "目标实例缺少 RAI 匹配的注册机构: " + node.getRai()));
        Namespace targetNs = namespaceRepository.findById(targetNamespaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Namespace", targetNamespaceId));

        // 来源命名空间原样迁移为副本（不关联注册机构，naming_authority 记录真实
        // 来源机构），经全局命名空间共享 UUID 与来源方命名空间建立映射
        Organization namingAuthority = globalNamespaceService.resolveNamingAuthority(
                snapshot.sourceNamespaceNamingAuthorityName(), ra);
        Namespace sourceCopy = globalNamespaceService.ensureNamespaceCopy(
                snapshot.sourceNamespaceGlobalId(), snapshot.sourceNamespaceName(), namingAuthority);

        if (snapshot.identifier() != null && scopedIdentifierRepository
                .existsByNamespaceIdAndIdentifier(sourceCopy.getId(), snapshot.identifier())) {
            throw new BusinessException(ApiCodes.CONFLICT,
                    "来源命名空间副本中已存在相同标识符（疑似重复并入）: " + snapshot.identifier());
        }

        itemAdministrationService.applyDesignations(item, toTextLines(snapshot.designations()));
        itemAdministrationService.applyDefinitions(item, toTextLines(snapshot.definitions()));
        // 来源标识（identifier + version）在来源命名空间副本下原样写入
        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item,
                new ItemRegistrationRequest(sourceCopy.getId(), snapshot.identifier(), snapshot.version(),
                        null, null),
                ra);

        // 目的方命名空间按标识生成策略生成新标识：迁移后条目持有双命名空间双标识
        ScopedIdentifier targetSi = new ScopedIdentifier(
                identifierStrategyConfigService.generateIdentifier(targetNs, null), targetNs, ai);
        targetSi.setVersion(Versions.initial(settingsService.getOrDefault(targetNs.getId()).getVersioningPolicy()));
        ai.getScopedIdentifiers().add(targetSi);
        itemRepository.save(item);
        // 生命周期扩展属性(createdAt 等)写入卫星实体;SI 经级联已持久化,ID 可用
        io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle targetLc =
                new io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle();
        targetLc.setCreatedAt(LocalDateTime.now());
        lifecycleService.stageNew(targetSi, targetLc);

        // MERGE 标记：记录本次迁移后目的方标识版本，供后续本地编辑检测
        identifierVersionHistoryRepository.save(new IdentifierVersionHistory(
                targetSi, targetSi.getVersion(), io.github.matrixlane.metadataregistry.ext.identification.VersionStatus.DRAFT,
                "system", MergeVersionSupport.MERGE_MARKER + " 迁移创建"));
        return ai;
    }

    /**
     * enforce-valid-period D4 导入兜底：值域/概念域 valid_period.start 必填
     * （ISO/IEC 11179-31:2023 表 5 / 表 2，§7.4.2.8.5 / §7.4.2.1.5.1）。
     * 快照来源数据缺有效日期时以导入执行时间补 start，保证迁移可用且满足约束。
     */
    private void applyImportValidPeriodFallback(Item item) {
        if (item instanceof ValueDomain vd
                && (vd.getValidPeriod() == null || vd.getValidPeriod().getStartDateTime() == null)) {
            Datetime_Period period = new Datetime_Period();
            period.setStartDateTime(OffsetDateTime.now());
            vd.setValidPeriod(period);
        }
        if (item instanceof ConceptualDomain cd
                && (cd.getValidPeriod() == null || cd.getValidPeriod().getStartDateTime() == null)) {
            Datetime_Period period = new Datetime_Period();
            period.setStartDateTime(OffsetDateTime.now());
            cd.setValidPeriod(period);
        }
    }

    private List<TextLine> toTextLines(List<LangValue> values) {
        if (values == null) {
            return null;
        }
        List<TextLine> lines = new java.util.ArrayList<>();
        for (LangValue v : values) {
            if (v.value() == null || v.languageIdentifier() == null) {
                continue;
            }
            UUID languageId = resolveLanguage(v.languageIdentifier());
            if (languageId == null) {
                // 目标实例没有该语言标识，跳过此行而不是让整个合并失败
                continue;
            }
            lines.add(new TextLine(languageId, v.value(), null, null));
        }
        return lines;
    }

    private UUID resolveLanguage(String identifier) {
        return languageRepository.findByLanguageIdentifier(identifier)
                .map(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification::getId)
                .orElse(null);
    }
}
