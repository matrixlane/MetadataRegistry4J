package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdministeredItemRepository
        extends JpaRepository<AdministeredItem, UUID>, JpaSpecificationExecutor<AdministeredItem> {

    Optional<AdministeredItem> findByItem(Item item);

    Optional<AdministeredItem> findByItem_Id(UUID itemId);

    @org.springframework.data.jpa.repository.Query("""
            SELECT ai.id, rs.registrationStatus
            FROM AdministeredItem ai
            LEFT JOIN ai.registrationState rs
            WHERE ai.item.id = :itemId
            """)
    java.util.List<Object[]> findMetaByItemId(@org.springframework.data.repository.query.Param("itemId") UUID itemId);

    @org.springframework.data.jpa.repository.Query("""
            SELECT ai FROM AdministeredItem ai WHERE ai.lastChangeDateTime > :since
            """)
    List<AdministeredItem> findChangedAfter(@org.springframework.data.repository.query.Param("since") LocalDateTime since);
}
