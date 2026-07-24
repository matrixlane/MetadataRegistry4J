package io.github.matrixlane.metadataregistry.web.federation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegistryNodeRepository extends JpaRepository<RegistryNode, UUID> {

    List<RegistryNode> findByEnabledTrue();
}
