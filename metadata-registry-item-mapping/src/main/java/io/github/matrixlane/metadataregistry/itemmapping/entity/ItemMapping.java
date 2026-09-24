package io.github.matrixlane.metadataregistry.itemmapping.entity;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 条目映射（Item_Mapping）。
 *
 * <p>Item_Mapping 是 Item 的直接子类，因此映射条目本身可被
 * 标识、命名、定义、分类并纳入注册状态流转。属性说明：
 * degree（1）、description（0..1）、transformation_rule（0..1）、
 * transformation_rule_notation（0..1，transformation_rule 存在时必填，
 * 由应用层校验）。
 *
 * <p>主体（subject）与客体（object）经关联类 {@link ItemMappingSubject} /
 * {@link ItemMappingObject} 绑定注册条目并携带 mapping_variable_name。
 * 映射层级经 {@link MappingHierarchy} 建模。
 */
@Entity
@Table(name = "item_mapping")
@Getter
@Setter
@NoArgsConstructor
public class ItemMapping extends Item {

    /**
     * 映射度（degree，必填），14 个标准值见 {@link MappingDegree}。
     * 持久化经 {@code MappingDegreeConverter} 落库标准小写值（@Enumerated 会
     * 覆盖 autoApply 转换器，故此处不标注）。
     */
    @Column(name = "degree", nullable = false, length = 32)
    private MappingDegree degree;

    /**
     * 映射的可读描述（description，0..1），尤其在 degree 为 related to 时提供。
     */
    @Column(name = "description", length = 2000)
    private String description;

    /**
     * 转换规则（transformation_rule，0..1）：把主体条目转换为
     * 客体条目的规则文本，可引用关联类上的 mapping_variable_name。
     */
    @Column(name = "transformation_rule", length = 4000)
    private String transformationRule;

    /**
     * 转换规则记法（transformation_rule_notation，0..1，条件必填）：
     * 描述转换规则的表示格式（如 EBNF）。transformation_rule 存在时必填。
     */
    @Column(name = "transformation_rule_notation", length = 255)
    private String transformationRuleNotation;

    /**
     * 映射主体绑定（Subject_Mapping 关联类侧写，1..*，子表持有外键）。
     * Item 与 Item_Mapping 为多对多关系，每个绑定携带各自的 mapping_variable_name。
     */
    @OneToMany(mappedBy = "itemMapping", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<ItemMappingSubject> subjects = new java.util.ArrayList<>();

    /**
     * 映射客体绑定（Object_Mapping 关联类侧写，1..*，子表持有外键）。
     */
    @OneToMany(mappedBy = "itemMapping", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<ItemMappingObject> objects = new java.util.ArrayList<>();
}
