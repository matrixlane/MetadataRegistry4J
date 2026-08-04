package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
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
     * URN-style scheme reference for this namespace.
     * Example: "urn:metadata-registry:system-default"
     */
    @jakarta.persistence.Column(name = "scheme_reference", length = 255)
    private String schemeReference;

    /**
     * 版本生成策略（如 SEMVER / SEQUENTIAL / DATE）。
     * 命名空间级设置，供该命名空间下条目版本自动递增生效。
     */
    @jakarta.persistence.Column(name = "versioning_policy", length = 32)
    private String versioningPolicy = "SEMVER";

    /**
     * 标识符输入模式（AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY）。
     * 控制该命名空间下标识符允许自动生成、手动覆盖还是纯手动。
     */
    @jakarta.persistence.Column(name = "identifier_input_mode", length = 32)
    private String identifierInputMode = "AUTO_WITH_OVERRIDE";

    /**
     * 版本号输入模式（AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY），独立于标识符。
     * AUTO 模式下版本号留空时按 versioningPolicy 生成初始版本。
     */
    @jakarta.persistence.Column(name = "version_input_mode", length = 32)
    private String versionInputMode = "AUTO_WITH_OVERRIDE";

    /**
     * 手动标识校验正则（可空）。配置后，手动填写的标识符必须匹配该正则。
     */
    @jakarta.persistence.Column(name = "manual_identifier_pattern", length = 255)
    private String manualIdentifierPattern;

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

    @OneToMany(mappedBy = "namespace", cascade = CascadeType.ALL)
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
