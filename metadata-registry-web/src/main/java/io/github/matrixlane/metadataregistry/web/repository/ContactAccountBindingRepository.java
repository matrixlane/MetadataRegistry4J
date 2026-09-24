package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.ext.basicandcore.ContactAccountBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactAccountBindingRepository extends JpaRepository<ContactAccountBinding, UUID> {

    Optional<ContactAccountBinding> findByContactId(UUID contactId);

    List<ContactAccountBinding> findByAccountId(UUID accountId);

    Optional<ContactAccountBinding> findByAccountIdAndIsDefaultTrue(UUID accountId);

    List<ContactAccountBinding> findByAccountIdOrderByContactIdAsc(UUID accountId);

    void deleteByContactId(UUID contactId);
}
