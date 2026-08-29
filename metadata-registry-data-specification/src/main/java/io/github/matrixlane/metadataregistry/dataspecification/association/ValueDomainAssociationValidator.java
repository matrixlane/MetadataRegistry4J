package io.github.matrixlane.metadataregistry.dataspecification.association;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;

/**
 * 值域（{@link ValueDomain}）关联关系校验器。
 */
public class ValueDomainAssociationValidator implements MetadataAssociationValidator {

    @Override
    public boolean supports(AssociationType type) {
        return type == AssociationType.VALUE_DOMAIN_TO_DATA_TYPE
                || type == AssociationType.VALUE_DOMAIN_TO_UNIT_OF_MEASURE;
    }

    @Override
    public void validate(AssociationType type, Item source, Item target) {
        if (!(source instanceof ValueDomain)) {
            throw new MetadataAssociationValidationException(
                    "关联类型 %s 的源对象必须是 ValueDomain".formatted(type));
        }
        switch (type) {
            case VALUE_DOMAIN_TO_DATA_TYPE -> {
                if (!(target instanceof DataType)) {
                    throw new MetadataAssociationValidationException(
                            "值域必须关联到 DataType，实际传入 %s".formatted(target.getClass().getSimpleName()));
                }
            }
            case VALUE_DOMAIN_TO_UNIT_OF_MEASURE -> {
                if (!(target instanceof UnitOfMeasure)) {
                    throw new MetadataAssociationValidationException(
                            "值域必须关联到 UnitOfMeasure，实际传入 %s".formatted(target.getClass().getSimpleName()));
                }
            }
            default -> throw new UnsupportedMetadataAssociationException("不支持的关联类型：" + type);
        }
    }

    @Override
    public void validateCompleteness(Item source) {
        // 数据类型与计量单位均为值域的可选关联，不做强制同时存在校验。
    }
}
