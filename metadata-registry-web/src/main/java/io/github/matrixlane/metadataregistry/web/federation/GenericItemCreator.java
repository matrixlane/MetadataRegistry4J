package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.Property;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.ItemAdministrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    private static final Map<String, Supplier<? extends Item>> TYPE_REGISTRY = Map.of(
            "DataElement", DataElement::new,
            "ObjectClass", ObjectClass::new,
            "Property", Property::new,
            "ConceptualDomain", ConceptualDomain::new,
            "ValueDomain", ValueDomain::new,
            "DataElementConcept", DataElementConcept::new,
            "DataType", DataType::new,
            "Context", Context::new
    );

    private final ItemAdministrationService itemAdministrationService;
    private final ItemRepository itemRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final LanguageIdentificationRepository languageRepository;

    public AdministeredItem create(BundleItemSnapshot snapshot, UUID targetNamespaceId, RegistryNode node) {
        String itemType = snapshot.export() == null ? null : snapshot.export().itemType();
        Supplier<? extends Item> constructor = TYPE_REGISTRY.get(itemType);
        if (constructor == null) {
            throw new BusinessException("暂不支持的条目类型: " + itemType
                    + "（支持: " + TYPE_REGISTRY.keySet() + "）");
        }
        Item item = constructor.get();

        RegistrationAuthority ra = registrationAuthorityRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("目标实例缺少注册机构，无法并入"));

        itemAdministrationService.applyDesignations(item, toTextLines(snapshot.designations()));
        itemAdministrationService.applyDefinitions(item, toTextLines(snapshot.definitions()));
        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(item,
                new ItemRegistrationRequest(targetNamespaceId, snapshot.identifier(), snapshot.version(),
                        ra.getId(), null, null));
        itemRepository.save(item);
        return ai;
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
