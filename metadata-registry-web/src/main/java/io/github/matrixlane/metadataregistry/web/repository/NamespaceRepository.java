package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NamespaceRepository
        extends JpaRepository<Namespace, UUID>, JpaSpecificationExecutor<Namespace> {

    boolean existsByShorthandPrefix(String shorthandPrefix);

    Optional<Namespace> findByShorthandPrefix(String shorthandPrefix);

    @Query("SELECT ns FROM Namespace ns WHERE ns.schemeReference = :schemeReference")
    Optional<Namespace> findBySchemeReference(@Param("schemeReference") String schemeReference);
}