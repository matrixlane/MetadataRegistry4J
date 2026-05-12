package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.web.entity.ApprovalTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalTemplateRepository extends JpaRepository<ApprovalTemplate, UUID> {

    List<ApprovalTemplate> findByEnabledTrue();

    Optional<ApprovalTemplate> findByIdAndEnabledTrue(UUID id);

    boolean existsByName(String name);
}
