package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.basicandcore.ContactExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContactExtensionRepository extends JpaRepository<ContactExtension, UUID> {

    List<ContactExtension> findByAccountId(UUID accountId);

    List<ContactExtension> findByAccountIdOrderByIdAsc(UUID accountId);
}
