package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
     * Namespace status lifecycle.
     * ISO/IEC 11179-3:2023 lifecycle + extended governance states.
     * Flow: DRAFT → SUBMITTED → ACTIVE → DEPRECATED → OBSOLETE
     */
    @jakarta.persistence.Column(name = "namespace_status", length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private NamespaceStatus namespaceStatus = NamespaceStatus.DRAFT;

    /**
     * Parent namespace for hierarchical namespace support.
     * ISO/IEC 11179-3:2023, 7.3.3 — namespaces can be organized in hierarchy.
     * Child namespace inherits namingAuthority and scheme_reference from parent.
     */
    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Namespace parent;

    /**
     * Plain-text naming authority identifier for built-in namespaces.
     * Example: "system" for the built-in system-default namespace.
     * This is a simpler alternative to the JPA reference field below.
     */
    @jakarta.persistence.Column(name = "naming_authority", length = 128)
    private String namingAuthority;

    @OneToMany(mappedBy = "namespace", cascade = CascadeType.ALL)
    private List<ScopedIdentifier> scopedIdentifiers = new ArrayList<>();

    /**
     * JPA reference to the formal RegistrationAuthority that governs this namespace.
     * May be null for built-in system namespaces (identified by namingAuthority="system").
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "naming_authority_id")
    private RegistrationAuthority registrationAuthority;

    /**
     * Bound NamingConvention ID when mandatoryNamingConventionIndicator = TRUE.
     * ISO/IEC 11179-3:2023, 8.4.4
     */
    @jakarta.persistence.Column(name = "naming_convention_id")
    private UUID namingConventionId;

    @OneToMany(mappedBy = "parent", cascade = jakarta.persistence.CascadeType.ALL)
    private List<Namespace> children = new ArrayList<>();
}