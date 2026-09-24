package io.github.matrixlane.metadataregistry.itemmapping.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Item_Mapping 映射度（degree）枚举。
 *
 * <p>共 14 个标准值，原文为小写加空格
 * （如 {@code related to}、{@code same as}）；标准明示遵从实现可扩展该值列表。
 * 语义方向遵循标准示例：degree 陈述以 subject 为视角指向 object
 * （如 subject "derived from" object 表示 object 由 subject 派生而来，
 * 见 isodate_mapping1 示例）。
 *
 * <p>Java 常量沿用大写下划线命名；{@link #standardValue()} 返回标准原文取值，
 * 落库与 JSON 序列化均使用标准值（小写），与原文档枚举值保持一致。
 */
public enum MappingDegree {

    /** 相关（最宽泛；此时 description 尤为适用） */
    RELATED_TO("related to"),

    /** 同一 */
    SAME_AS("same as"),

    /** 语义等价 */
    SEMANTICALLY_EQUIVALENT("semantically equivalent"),

    /** 语义相似 */
    SEMANTICALLY_SIMILAR("semantically similar"),

    /** 派生自：object 由 subject 派生 */
    DERIVED_FROM("derived from"),

    /** 有派生：subject 由 object 派生（derived from 的逆） */
    HAS_DERIVATION("has derivation"),

    /** 宽于：object 比 subject 更宽泛 */
    BROADER_THAN("broader than"),

    /** 窄于：object 比 subject 更狭窄 */
    NARROWER_THAN("narrower than"),

    /** 子级：subject 是 object 的下级 */
    CHILD_OF("child of"),

    /** 父级：subject 是 object 的上级 */
    PARENT_OF("parent of"),

    /** 包含：subject 包含 object */
    CONTAINS("contains"),

    /** 被包含：subject 被 object 包含 */
    CONTAINED_IN("contained in"),

    /** 实例：subject 是 object 的实例 */
    INSTANCE_OF("instance of"),

    /** 有实例：object 是 subject 的实例（instance of 的逆） */
    HAS_INSTANCE("has instance");

    private final String standardValue;

    MappingDegree(String standardValue) {
        this.standardValue = standardValue;
    }

    /**
     * 标准取值（小写原文），落库与 JSON 均使用该值。
     */
    @JsonValue
    public String standardValue() {
        return standardValue;
    }

    /**
     * 对称映射度：subject/object 互换语义不变（same as、semantically equivalent）。
     * 影响分析等场景据此判定双向影响。
     */
    public boolean isSymmetric() {
        return this == SAME_AS || this == SEMANTICALLY_EQUIVALENT;
    }

    /**
     * 按标准值解析（大小写不敏感，兼容历史大写枚举名）。
     *
     * @param value 标准小写值（如 "related to"）或枚举常量名（如 "RELATED_TO"）
     * @return 对应枚举；空值返回 null
     * @throws IllegalArgumentException 无法识别时
     */
    @JsonCreator
    public static MappingDegree fromStandardValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        for (MappingDegree degree : values()) {
            if (degree.standardValue.equalsIgnoreCase(v) || degree.name().equalsIgnoreCase(v)) {
                return degree;
            }
        }
        throw new IllegalArgumentException("Unknown mapping degree: " + value);
    }
}
