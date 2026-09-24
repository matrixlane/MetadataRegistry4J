package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.StewardshipRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StewardshipRecordRepository extends JpaRepository<StewardshipRecord, UUID> {

    Optional<StewardshipRecord> findFirstByContact_IdAndOrganization_Id(UUID contactId, UUID organizationId);
}
