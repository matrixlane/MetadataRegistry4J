package io.github.matrixlane.metadataregistry.web.service.association;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.Dimensionality;
import io.github.matrixlane.metadataregistry.dataspecification.MeasureClass;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;

/**
 * 值域（{@link ValueDomain}）关联关系校验器。
 */
public class ValueDomainAssociationValidator implements MetadataAssociationValidator {

    @Override
    public boolean supports(AssociationType type) {
        return type == AssociationType.VALUE_DOMAIN_TO_DATA_TYPE
                || type == AssociationType.VALUE_DOMAIN_TO_UNIT_OF_MEASURE
                || type == AssociationType.VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN;
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
                if (!(target instanceof UnitOfMeasure unitOfMeasure)) {
                    throw new MetadataAssociationValidationException(
                            "值域必须关联到 UnitOfMeasure，实际传入 %s".formatted(target.getClass().getSimpleName()));
                }
                // 概念域已指定维度时，值域的计量单位必须与该维度一致。
                assertConsistentDimensionality(unitOfMeasure,
                        currentConceptualDomainDimensionality((ValueDomain) source));
            }
            case VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN -> {
                // ISO/IEC 11179-31:2023 §7.4.5.2.1：值域与概念域子类型必须一致，
                // 由 ValueDomainConceptualDomains.set 在写入时最终强制。
                if (!(target instanceof ConceptualDomain conceptualDomain)) {
                    throw new MetadataAssociationValidationException(
                            "值域必须关联到 ConceptualDomain，实际传入 %s".formatted(target.getClass().getSimpleName()));
                }
                // 值域已挂计量单位且概念域指定维度时，二者必须一致。
                assertConsistentDimensionality(((ValueDomain) source).getUnitOfMeasure(),
                        conceptualDomain.getDimensionality());
            }
            default -> throw new UnsupportedMetadataAssociationException("不支持的关联类型：" + type);
        }
    }

    /**
     * 一致性：
     * 概念域的 dimensionality 与值域的 unitOfMeasure 均已指定时,
     * 计量单位所属计量类必须与维度的计量类存在交集;任一侧未指定不校验
     * (约束以"指定时"为前提)。
     */
    private static void assertConsistentDimensionality(
            UnitOfMeasure unitOfMeasure, Dimensionality dimensionality) {
        if (unitOfMeasure == null || dimensionality == null) {
            return;
        }
        java.util.Set<java.util.UUID> unitClasses = unitOfMeasure.getMeasureClasses().stream()
                .map(MeasureClass::getId)
                .collect(java.util.stream.Collectors.toSet());
        boolean consistent = dimensionality.getMeasureClasses().stream()
                .map(MeasureClass::getId)
                .anyMatch(unitClasses::contains);
        if (!consistent) {
            throw new MetadataAssociationValidationException(
                    "计量单位所属计量类与概念域维度不一致");
        }
    }

    private static Dimensionality currentConceptualDomainDimensionality(ValueDomain valueDomain) {
        ConceptualDomain current = ValueDomainConceptualDomains.get(valueDomain);
        return current == null ? null : current.getDimensionality();
    }

    @Override
    public void validateCompleteness(Item source) {
        // ISO/IEC 11179-31:2023 表 5(Value_Domain)：datatype 基数为 1（必备），
        // 实体映射为 @ManyToOne(optional=false)。缺省持久化会产生无法经
        // findById 加载的畸形行（inner join 丢行），保存前必须拦下。
        // 计量单位为可选关联，不强制。
        if (source instanceof ValueDomain valueDomain && valueDomain.getDataType() == null) {
            throw new MetadataAssociationValidationException(
                    "值域必须关联数据类型（ISO/IEC 11179-31:2023 表 5 datatype 基数为 1）");
        }
        // 表 5 同表：valid_period 基数为 1，且须含 start_datetime
        // （§7.4.2.8.5）；end 可选（缺省表示仍有效）。校验在 datatype 之后。
        if (source instanceof ValueDomain valueDomain
                && (valueDomain.getValidPeriod() == null
                || valueDomain.getValidPeriod().getStartDateTime() == null)) {
            throw new MetadataAssociationValidationException(
                    "值域必须填写有效日期（起始时间）（ISO/IEC 11179-31:2023 表 5 valid_period 基数为 1，"
                            + "§7.4.2.8.5 须含 start_datetime）");
        }
    }
}
