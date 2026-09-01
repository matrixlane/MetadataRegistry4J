package io.github.matrixlane.metadataregistry.web.service.association;

/**
 * 允许的元数据对象关联类型。
 * <p>
 * 枚举值以关联的“拥有方”命名，对应 JPA 实体中维护外键或连接表的一方：
 * <ul>
 *   <li>{@link io.github.matrixlane.metadataregistry.dataspecification.DataElement}</li>
 *   <li>{@link io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept}</li>
 *   <li>{@link io.github.matrixlane.metadataregistry.dataspecification.ValueDomain}</li>
 *   <li>{@link io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain}</li>
 * </ul>
 */
public enum AssociationType {

    DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT,
    DATA_ELEMENT_TO_VALUE_DOMAIN,

    DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS,
    DATA_ELEMENT_CONCEPT_TO_PROPERTY,
    DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN,

    VALUE_DOMAIN_TO_DATA_TYPE,
    VALUE_DOMAIN_TO_UNIT_OF_MEASURE,

    /** value_domain_meaning（ISO/IEC 11179-31:2023 §7.4.4.1）：值域到概念域，按 §7.4.5.2.1 要求子类型一致。 */
    VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN
}
