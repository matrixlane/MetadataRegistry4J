package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.web.entity.VersionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VersionHistoryRepository extends JpaRepository<VersionHistory, Long> {

    List<VersionHistory> findByItemIdOrderByVersionNumberDesc(UUID itemId);

    Optional<VersionHistory> findByItemIdAndVersionNumber(UUID itemId, Integer versionNumber);

    Optional<VersionHistory> findTopByItemIdOrderByVersionNumberDesc(UUID itemId);

    // Derived delete requires an active transaction; give it one so callers
    // (e.g. test fixtures) do not need to wrap the call themselves.
    @Transactional
    void deleteAllByItemId(UUID itemId);
}
