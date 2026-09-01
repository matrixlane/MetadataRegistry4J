package io.github.matrixlane.metadataregistry.ext.designationanddefinition;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * NamingConvention 机器可执行规则的卫星实体(扩展,非 ISO/IEC 11179 标准模型)。
 *
 * <p>标准实体 {@code NamingConvention}(ISO/IEC 11179-3:2023, 7.3.4)的规则为
 * scope/authority/semantic/syntactic/lexical 五个标准属性;rulesJson(JSON 序列化的
 * 机器规则,供校验引擎直接执行)为注册表软件自身扩展,剥离至本卫星实体
 * (表 naming_convention_rules,按 naming_convention_id 一一对应)。
 *
 * <p>有意不建立到 NamingConvention 的 JPA 关联:避免卫星加载反向触发
 * 标准实体对象图,读写一律经值键。缺失行语义等同 rulesJson=null
 * (引擎回退到标准属性生成规则)。阶段 3 起归入 EXT schema。
 */
@jakarta.persistence.Entity
@Table(name = "naming_convention_rules")
@Getter
@Setter
@NoArgsConstructor
public class NamingConventionRules {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "naming_convention_id", nullable = false, unique = true)
    private UUID namingConventionId;

    @Lob
    @Column(name = "rules_json")
    private String rulesJson;

    public NamingConventionRules(UUID namingConventionId) {
        this.namingConventionId = namingConventionId;
    }
}
