package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ReferenceDocumentRepository
        extends JpaRepository<ReferenceDocument, UUID>, JpaSpecificationExecutor<ReferenceDocument> {

    boolean existsByIdentifier(String identifier);
}
