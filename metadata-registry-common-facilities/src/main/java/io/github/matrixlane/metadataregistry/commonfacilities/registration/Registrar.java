package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class Registrar extends Contact {

    private String identifier;

    @ManyToOne
    @JoinColumn(name = "registration_authority_id")
    private RegistrationAuthority registrationAuthority;

    public Registrar(RegistrationAuthority registrationAuthority) {
        super(registrationAuthority);
        this.registrationAuthority = registrationAuthority;
    }
}
