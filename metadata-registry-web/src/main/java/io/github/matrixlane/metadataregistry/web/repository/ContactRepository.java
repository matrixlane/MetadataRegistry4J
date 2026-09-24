package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ContactRepository
        extends JpaRepository<Contact, UUID>, JpaSpecificationExecutor<Contact> {

    /** 统计引用某 Role 的 Contact（含 Registrar 子类，JOINED 继承外键同表）。 */
    long countByRoleId(UUID roleId);
}
