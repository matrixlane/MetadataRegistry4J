package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinColumn;
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
public class Namespace extends Item {

    @OneToMany(mappedBy = "namespace", cascade = CascadeType.ALL)
    private List<ScopedIdentifier> scopedIdentifiers = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "naming_authority_id")
    private RegistrationAuthority namingAuthority;
}
