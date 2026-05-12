package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Notation;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ISO/IEC 11179-3:2023, 6.3.7 — Reference_Document
 *
 * <p>A document that provides information supporting the content of a registry item.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.3.7")
@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class ReferenceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String identifier;

    @ManyToOne
    @JoinColumn(name = "type_description_id")
    private DocumentType typeDescription;

    @ManyToMany
    @JoinTable(
            name = "reference_document_language",
            joinColumns = @JoinColumn(name = "reference_document_id"),
            inverseJoinColumns = @JoinColumn(name = "language_id")
    )
    private List<LanguageIdentification> language = new ArrayList<>();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "notation_value"))
    })
    private Notation notation;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "title_value"))
    })
    private Text title;

    @ManyToMany
    @JoinTable(
            name = "reference_document_provider",
            joinColumns = @JoinColumn(name = "reference_document_id"),
            inverseJoinColumns = @JoinColumn(name = "provider_id")
    )
    private List<Organization> provider = new ArrayList<>();

    private String uri;
}
