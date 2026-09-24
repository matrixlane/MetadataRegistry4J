package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByRoleName(String roleName);

    Optional<Role> findByRoleNameIgnoreCase(String roleName);

    boolean existsByRoleNameIgnoreCase(String roleName);

    Page<Role> findByRoleNameContainingIgnoreCase(String roleName, Pageable pageable);
}
