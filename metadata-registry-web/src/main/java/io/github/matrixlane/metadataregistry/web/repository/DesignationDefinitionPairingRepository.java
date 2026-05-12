package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationDefinitionPairing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DesignationDefinitionPairingRepository extends JpaRepository<DesignationDefinitionPairing, UUID> {

    List<DesignationDefinitionPairing> findByDesignation_Item_Id(UUID itemId);

    void deleteByDesignation_Item_Id(UUID itemId);
}
