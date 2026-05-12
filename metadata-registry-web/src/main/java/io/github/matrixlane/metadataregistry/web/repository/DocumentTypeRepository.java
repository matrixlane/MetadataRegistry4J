package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, UUID> {

    boolean existsByIdentifier(String identifier);
}
