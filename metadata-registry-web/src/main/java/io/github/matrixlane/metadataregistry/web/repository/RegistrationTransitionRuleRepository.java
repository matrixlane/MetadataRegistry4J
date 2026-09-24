package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationTransitionRuleRepository
        extends JpaRepository<RegistrationTransitionRule, UUID> {

    Optional<RegistrationTransitionRule> findByRegistrationAuthorityIdAndFromStatusAndToStatus(
            UUID registrationAuthorityId, RegistrationStatus fromStatus, RegistrationStatus toStatus);

    boolean existsByRegistrationAuthorityIdAndFromStatusAndToStatus(
            UUID registrationAuthorityId, RegistrationStatus fromStatus, RegistrationStatus toStatus);

    List<RegistrationTransitionRule> findByRegistrationAuthorityId(UUID registrationAuthorityId);

    List<RegistrationTransitionRule> findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(
            UUID registrationAuthorityId, RegistrationStatus fromStatus);
}
