package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 8.4.4 — Naming_Convention.
 *
 * <p>A naming convention provides a set of rules for constructing the sign of a designation
 *
 * <p>扩展属性 rulesJson(机器可执行规则 JSON)非标准模型,已剥离至卫星实体
 * {@code ext.designationanddefinition.NamingConventionRules}(表 naming_convention_rules),
 * 读写经 web 层 NamingConventionRulesService,缺失行等同 rulesJson=null。
 * of a registry item. It is a subclass of Item and can therefore be identified, registered,
 * administered, named, defined and classified.
 */
@StandardReference("ISO/IEC 11179-3:2023, 8.4.4")
@Entity
@Getter
@Setter
@NoArgsConstructor
public class NamingConvention extends Item {

    /**
     * Rule specifying the range within which the naming convention is in effect.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "scope_rule"))
    })
    private Text scopeRule;

    /**
     * Rule identifying the authority that assigns names or enforces naming conventions, or both.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "authority_rule"))
    })
    private Text authorityRule;

    /**
     * Semantic rule: what concepts the name should express.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "semantic_rule"))
    })
    private Text semanticRule;

    /**
     * Syntactic rule: how words should be ordered or structured.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "syntactic_rule"))
    })
    private Text syntacticRule;

    /**
     * Lexical rule: what character set or vocabulary may be used.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "lexical_rule"))
    })
    private Text lexicalRule;
}
