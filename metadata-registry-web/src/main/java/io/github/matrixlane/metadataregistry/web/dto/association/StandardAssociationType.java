package io.github.matrixlane.metadataregistry.web.dto.association;

import io.github.matrixlane.metadataregistry.web.common.BusinessException;

/**
 * 数据元、数据元概念、概念域、值域四实体之间的标准关联
 * （ISO/IEC 11179-31:2023 §7.2.3 / §7.4.4.1 / §7.6.3）。
 * <p>
 * 每个枚举值标注该关联在标准中的“源 → 目标”方向，写操作按此方向落库；
 * 查询操作会同时汇集焦点实体的正向与反向实例。
 */
public enum StandardAssociationType {

    /** data_element_meaning（§7.2.3.3 / §7.6.3.2）：Data_Element 0..* → 0..1 Data_Element_Concept。 */
    DATA_ELEMENT_MEANING("data-element-meaning"),

    /** data_element_domain（§7.2.3.2 / §7.6.3.1）：Data_Element 0..* → 0..1 Value_Domain。 */
    DATA_ELEMENT_DOMAIN("data-element-domain"),

    /** value_domain_meaning（§7.2.3.1 / §7.4.4.1）：Value_Domain 0..* → 0..1 Conceptual_Domain。 */
    VALUE_DOMAIN_MEANING("value-domain-meaning"),

    /** data_element_concept_domain（§7.2.3.4）：Data_Element_Concept 0..* ↔ 0..* Conceptual_Domain。 */
    DATA_ELEMENT_CONCEPT_DOMAIN("data-element-concept-domain");

    private final String pathValue;

    StandardAssociationType(String pathValue) {
        this.pathValue = pathValue;
    }

    public String pathValue() {
        return pathValue;
    }

    public static StandardAssociationType fromPath(String value) {
        for (StandardAssociationType type : values()) {
            if (type.pathValue.equals(value)) {
                return type;
            }
        }
        throw new BusinessException("未知的关联类型：" + value);
    }
}
