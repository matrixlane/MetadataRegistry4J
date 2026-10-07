package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import jakarta.persistence.Embedded;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class RegistrationAuthority extends Organization {

    @Embedded
    private RegistrationAuthorityIdentifier registrationAuthorityIdentifier;

    @ManyToMany
    @JoinTable(
            name = "registration_authority_doc_language",
            joinColumns = @JoinColumn(name = "registration_authority_id"),
            inverseJoinColumns = @JoinColumn(name = "language_id")
    )
    private List<LanguageIdentification> documentationLanguageIdentifier = new ArrayList<>();

    @OneToMany(mappedBy = "registrationAuthority")
    private List<Registrar> registrars = new ArrayList<>();

    @OneToOne(mappedBy = "registrationAuthority")
    private Namespace namespace;
}
