package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import java.util.List;

public interface ContactRepository
        extends JpaRepository<Contact, UUID>, JpaSpecificationExecutor<Contact> {
    List<Contact> findByAccount(Account account);
}
