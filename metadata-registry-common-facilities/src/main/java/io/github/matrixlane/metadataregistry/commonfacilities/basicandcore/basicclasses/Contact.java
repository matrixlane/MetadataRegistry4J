package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ISO/IEC 11179-3:2023, 6.3.1 — Contact
 *
 * <p>Contact information linking an {@link Individual}, an {@link Organization},
 * and a {@link Role}.
 */
@StandardReference("ISO/IEC 11179-3:2023, 6.3.1")
@jakarta.persistence.Entity
@jakarta.persistence.Inheritance(strategy = jakarta.persistence.InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public class Contact extends Entity {

    @ManyToOne
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne
    @JoinColumn(name = "individual_id")
    private Individual individual;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    public Contact(Organization organization) {
        this.organization = organization;
    }
}
