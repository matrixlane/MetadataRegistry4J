package io.github.matrixlane.metadataregistry.web.service.association;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.Property;

/**
 * 数据元概念（{@link DataElementConcept}）关联关系校验器。
 * <p>
 * 依据 ISO/IEC 11179-3，数据元概念由对象类与属性组合定义，因此完整的数据元概念必须同时具有二者。
 */
public class DataElementConceptAssociationValidator implements MetadataAssociationValidator {

    @Override
    public boolean supports(AssociationType type) {
        return type == AssociationType.DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS
                || type == AssociationType.DATA_ELEMENT_CONCEPT_TO_PROPERTY
                || type == AssociationType.DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN;
    }

    @Override
    public void validate(AssociationType type, Item source, Item target) {
        if (!(source instanceof DataElementConcept)) {
            throw new MetadataAssociationValidationException(
                    "关联类型 %s 的源对象必须是 DataElementConcept".formatted(type));
        }
        switch (type) {
            case DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS -> {
                if (!(target instanceof ObjectClass)) {
                    throw new MetadataAssociationValidationException(
                            "数据元概念必须关联到 ObjectClass，实际传入 %s".formatted(target.getClass().getSimpleName()));
                }
            }
            case DATA_ELEMENT_CONCEPT_TO_PROPERTY -> {
                if (!(target instanceof Property)) {
                    throw new MetadataAssociationValidationException(
                            "数据元概念必须关联到 Property，实际传入 %s".formatted(target.getClass().getSimpleName()));
                }
            }
            case DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN -> {
                if (!(target instanceof ConceptualDomain)) {
                    throw new MetadataAssociationValidationException(
                            "数据元概念必须关联到 ConceptualDomain，实际传入 %s".formatted(target.getClass().getSimpleName()));
                }
            }
            default -> throw new UnsupportedMetadataAssociationException("不支持的关联类型：" + type);
        }
    }

    @Override
    public void validateCompleteness(Item source) {
        // 与 DataElement 类似，objectClass 与 property 为可选关联，
        // 当前不在保存前强制要求二者同时存在。
    }
}
