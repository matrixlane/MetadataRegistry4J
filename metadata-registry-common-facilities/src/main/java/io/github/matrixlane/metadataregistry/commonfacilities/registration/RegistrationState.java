package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@jakarta.persistence.Entity
@Getter
@Setter
@NoArgsConstructor
public class RegistrationState extends Entity {

    @Enumerated(EnumType.STRING)
    private RegistrationStatus registrationStatus;

    private LocalDateTime effectiveDate;

    private LocalDateTime untilDate;

    private String administrativeNote;

    private String unresolvedIssue;

    private String administrativeStatus;

    @OneToOne
    @JoinColumn(name = "previous_state_id")
    private RegistrationState previousState;
}
