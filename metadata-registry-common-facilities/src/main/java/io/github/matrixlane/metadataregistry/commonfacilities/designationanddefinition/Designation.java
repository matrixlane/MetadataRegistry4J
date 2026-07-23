package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
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

@jakarta.persistence.Entity
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

    @ManyToOne
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToMany
    @JoinTable(
            name = "designation_namespace",
            joinColumns = @JoinColumn(name = "designation_id"),
            inverseJoinColumns = @JoinColumn(name = "namespace_id")
    )
    private List<Namespace> namespaces = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "designation_context",
            joinColumns = @JoinColumn(name = "designation_id"),
            inverseJoinColumns = @JoinColumn(name = "context_id")
    )
    private List<Context> contexts = new ArrayList<>();

    @OneToMany(mappedBy = "designation")
    private List<DesignationContext> designationContexts = new ArrayList<>();

    @OneToMany(mappedBy = "designation")
    private List<DesignationDefinitionPairing> designationDefinitionPairings = new ArrayList<>();

    /**
     * ISO/IEC 11179-3:2023, 8.6.5 — naming_convention_conformance association.
     * Naming conventions to which this designation conforms.
     */
    @ManyToMany
    @JoinTable(
            name = "designation_naming_convention",
            joinColumns = @JoinColumn(name = "designation_id"),
            inverseJoinColumns = @JoinColumn(name = "naming_convention_id")
    )
    private List<NamingConvention> namingConventions = new ArrayList<>();

    @Override
    public String toString() {
        return sign == null ? null : sign.toString();
    }
}
