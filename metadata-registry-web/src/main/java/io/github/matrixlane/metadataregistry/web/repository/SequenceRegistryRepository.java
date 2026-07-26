package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.SequenceRegistry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SequenceRegistryRepository extends JpaRepository<SequenceRegistry, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SequenceRegistry r WHERE r.namespace.id = :namespaceId")
    Optional<SequenceRegistry> findByNamespaceIdWithLock(@Param("namespaceId") UUID namespaceId);
}
