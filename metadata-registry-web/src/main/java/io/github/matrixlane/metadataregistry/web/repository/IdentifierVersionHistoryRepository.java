package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.identification.IdentifierVersionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IdentifierVersionHistoryRepository
        extends JpaRepository<IdentifierVersionHistory, UUID> {

    List<IdentifierVersionHistory> findByScopedIdentifierIdOrderByTimestampDesc(UUID scopedIdentifierId);
}
