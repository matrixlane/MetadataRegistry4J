package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DesignationRepository extends JpaRepository<Designation, UUID> {

    List<Designation> findByItem_Id(UUID itemId);
}
