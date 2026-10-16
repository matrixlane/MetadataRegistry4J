package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import jakarta.persistence.Embedded;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@StandardReference("ISO/IEC 11179-3:2023, 8.4.1")
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "designation", indexes = {
        @jakarta.persistence.Index(name = "idx_designation_item_id", columnList = "item_id"),
        @jakarta.persistence.Index(name = "idx_designation_language_id", columnList = "language_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Designation extends Entity {

    @Embedded
    private Sign sign;

    @ManyToOne
    @JoinColumn(name = "language_id")
    private LanguageIdentification language;

    @ManyToOne
    @JoinColumn(name = "source_id")
    private ReferenceDocument source;

    // LAZY：item 为 JOINED 继承的层级根，默认 EAGER 会让 Designation 的单行加载
    // 多态连接全部 Item 子类表，超出 SQLite 的 64 表 join 上限（配对/校验等
    // 流程只需读取外键 id，代理解析即可，无需初始化）。
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToMany
    @JoinTable(
            name = "designation_namespace",
            joinColumns = @JoinColumn(name = "designation_id"),
            inverseJoinColumns = @JoinColumn(name = "namespace_id")
    )
    private List<Namespace> namespaces = new ArrayList<>();

    // cascade + orphanRemoval：DesignationContext 的持久化由本侧驱动（inverse 集合
    // 无级联时 flush 从不落库，designation_context 恒为空表）。
    @OneToMany(mappedBy = "designation", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<DesignationContext> designationContexts = new ArrayList<>();

    // cascade + orphanRemoval：配对行的持久化生命周期挂在指称上；条目删除经
    // Item.designations 级联到指称时，若不级联删除配对，designation_definition_pairing
    // 的外键会先挡住 definition/designation 的删除（409 数据冲突）。
    @OneToMany(mappedBy = "designation", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<DesignationDefinitionPairing> designationDefinitionPairings = new ArrayList<>();

    /**
     * ISO/IEC 11179-3:2023, 8.6.5 — naming_convention_conformance association.
     * Naming conventions to which this designation conforms.
     */
    @ManyToMany
    @JoinTable(
            name = "naming_convention_conformance",
            joinColumns = @JoinColumn(name = "designation_id"),
            inverseJoinColumns = @JoinColumn(name = "naming_convention_id")
    )
    private List<NamingConvention> namingConventions = new ArrayList<>();

    @Override
    public String toString() {
        return sign == null ? null : sign.toString();
    }
}
