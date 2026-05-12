package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DefinitionRepository extends JpaRepository<Definition, UUID> {

    List<Definition> findByItem_Id(UUID itemId);
}
