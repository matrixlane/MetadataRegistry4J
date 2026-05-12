package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdentifiedItemRepository extends JpaRepository<IdentifiedItem, UUID> {

    Optional<IdentifiedItem> findByItem(Item item);
}
