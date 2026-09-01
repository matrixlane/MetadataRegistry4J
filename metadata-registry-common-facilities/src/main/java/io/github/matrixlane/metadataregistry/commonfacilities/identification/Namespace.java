package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * ISO/IEC 11179-3:2023 表 13(Namespace)。
 *
 * <p>标识符治理扩展属性(versioningPolicy/identifierInputMode/versionInputMode/
 * manualIdentifierPattern)已剥离至 ext 模块卫星实体
 * {@code NamespaceIdentifierSettings}(表 namespace_identifier_settings,
 * 按 namespace_id 一一对应,有意不建 JPA 关联)。
 */
@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class Namespace extends Item {

    /**
     * Shorthand prefix for identifiers within this namespace (e.g. "sys:").
     * Used as a quick qualifier to distinguish namespaces.
     */
    @jakarta.persistence.Column(name = "shorthand_prefix", length = 32)
    private String shorthandPrefix;

    /**
     * ISO/IEC 11179-3:2023 表 13(Namespace):scheme_reference 0..1 Sign (6.2.10),
     * IRI 形式的方案引用。例:"urn:metadata-registry:system-default"
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "scheme_reference", length = 255))
    })
    private Sign schemeReference;

    /**
     * When TRUE, all Items in this namespace must pass NamingConvention validation
     * before being saved.
     */
    @jakarta.persistence.Column(name = "mandatory_naming_convention_indicator")
    private Boolean mandatoryNamingConventionIndicator = false;

    /**
     * When TRUE, only one Item with a given name is allowed per namespace.
     */
    @jakarta.persistence.Column(name = "one_item_per_name_indicator")
    private Boolean oneItemPerNameIndicator = true;

    /**
     * When TRUE, only one name is allowed per Item within this namespace.
     * ISO/IEC 11179-3:2023, 7.3.3
     */
    @jakarta.persistence.Column(name = "one_name_per_item_indicator")
    private Boolean oneNamePerItemIndicator = false;

    /**
     * ISO/IEC 11179-3:2023, Table 13 — naming_authority.
     * Organization that has the authority for naming in this namespace.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "naming_authority_id")
    private Organization namingAuthority;

    /**
     * ISO/IEC 11179-3:2023 7.3.2.4:Namespace 删除时,
     * 经 identifier_scope 关联的 Scoped_Identifier 须一并删除。
     */
    @OneToMany(mappedBy = "namespace", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScopedIdentifier> scopedIdentifiers = new ArrayList<>();

    /**
     * ISO/IEC 11179-3:2023, 9.5.5 — registration_authority_namespace association.
     * The RegistrationAuthority whose registration authority identifier is scoped by this namespace.
     * Navigability is from Namespace to RegistrationAuthority in this implementation.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_authority_id")
    private RegistrationAuthority registrationAuthority;

    /**
     * ISO/IEC 11179-3:2023, 8.6.6 — naming_convention_utilization association.
     * Naming conventions used by this namespace.
     */
    @ManyToMany
    @JoinTable(
            name = "namespace_naming_convention",
            joinColumns = @JoinColumn(name = "namespace_id"),
            inverseJoinColumns = @JoinColumn(name = "naming_convention_id")
    )
    private List<NamingConvention> namingConventions = new ArrayList<>();
}
