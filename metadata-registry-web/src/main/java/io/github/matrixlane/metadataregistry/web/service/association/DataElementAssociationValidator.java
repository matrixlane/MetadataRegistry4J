package io.github.matrixlane.metadataregistry.web.service.association;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;

/**
 * 数据元（{@link DataElement}）关联关系校验器。
 * <p>
 * 依据 ISO/IEC 11179-3，数据元由数据元概念与值域共同派生，因此完整的数据元必须同时具有二者。
 */
public class DataElementAssociationValidator implements MetadataAssociationValidator {

    @Override
    public boolean supports(AssociationType type) {
        return type == AssociationType.DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT
                || type == AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN;
    }

    @Override
    public void validate(AssociationType type, Item source, Item target) {
        if (!(source instanceof DataElement)) {
            throw new MetadataAssociationValidationException(
                    "关联类型 %s 的源对象必须是 DataElement".formatted(type));
        }
        switch (type) {
            case DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT -> {
                if (!(target instanceof DataElementConcept)) {
                    throw new MetadataAssociationValidationException(
                            "数据元必须关联到 DataElementConcept，实际传入 %s".formatted(target.getClass().getSimpleName()));
                }
            }
            case DATA_ELEMENT_TO_VALUE_DOMAIN -> {
                if (!(target instanceof ValueDomain)) {
                    throw new MetadataAssociationValidationException(
                            "数据元必须关联到 ValueDomain，实际传入 %s".formatted(target.getClass().getSimpleName()));
                }
            }
            default -> throw new UnsupportedMetadataAssociationException("不支持的关联类型：" + type);
        }
    }

    @Override
    public void validateCompleteness(Item source) {
        // ISO/IEC 11179-31:2023 中 data_element_meaning 与 data_element_domain 的下界均为 0，
        // 因此数据元概念与值域为可选关联，此处不做强制同时存在校验。
        // 如需在特定注册状态（如已注册/有效）下强制完整，可扩展按状态分层的校验器。
    }
}
