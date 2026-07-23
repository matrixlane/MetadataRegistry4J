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
     * Parent namespace for hierarchical namespace support.
     * ISO/IEC 11179-3:2023, 7.3.3 — namespaces can be organized in hierarchy.
     * Child namespace inherits namingAuthority and scheme_reference from parent.
     */
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Namespace parent;

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

    @OneToMany(mappedBy = "parent", cascade = jakarta.persistence.CascadeType.ALL)
    private List<Namespace> children = new ArrayList<>();
}
