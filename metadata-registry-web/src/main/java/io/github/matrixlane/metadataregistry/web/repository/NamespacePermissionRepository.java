package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.web.entity.NamespacePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NamespacePermissionRepository extends JpaRepository<NamespacePermission, UUID> {

    List<NamespacePermission> findByNamespaceId(UUID namespaceId);

    Optional<NamespacePermission> findByNamespaceIdAndAccountId(UUID namespaceId, UUID accountId);

    Optional<NamespacePermission> findByNamespaceIdAndOrganizationId(UUID namespaceId, UUID organizationId);

    List<NamespacePermission> findByAccountId(UUID accountId);

    List<NamespacePermission> findByOrganizationId(UUID organizationId);
}
