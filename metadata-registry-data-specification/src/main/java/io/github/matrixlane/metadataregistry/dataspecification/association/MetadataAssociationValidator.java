package io.github.matrixlane.metadataregistry.dataspecification.association;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;

/**
 * 元数据对象关联校验器接口。
 * <p>
 * 每个实现类负责一种或多种 {@link AssociationType} 的校验逻辑。
 */
public interface MetadataAssociationValidator {

    /**
     * 该校验器是否支持指定的关联类型。
     */
    boolean supports(AssociationType type);

    /**
     * 校验单次关联操作是否合法。
     *
     * @param type   关联类型
     * @param source 关联的拥有方
     * @param target 关联的目标方
     * @throws MetadataAssociationValidationException 校验失败时抛出
     */
    void validate(AssociationType type, Item source, Item target);

    /**
     * 校验对象在保存前的完整关联状态是否满足业务规则。
     *
     * @param source 待保存的元数据对象
     * @throws MetadataAssociationValidationException 校验失败时抛出
     */
    void validateCompleteness(Item source);
}
