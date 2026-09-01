package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;

class SubmissionRecordTest {

    @Test
    void shouldInitializeRegisteredItems() {
        SubmissionRecord record = new SubmissionRecord();
        assertThat(record.getRegisteredItems()).isEmpty();
    }

    @Test
    void shouldSetOrganization() {
        SubmissionRecord record = new SubmissionRecord();
        Organization org = new Organization();
        record.setOrganization(org);
        assertThat(record.getOrganization()).isEqualTo(org);
    }

    @Test
    void shouldSetContact() {
        SubmissionRecord record = new SubmissionRecord();
        Contact contact = new Contact();
        record.setContact(contact);
        assertThat(record.getContact()).isEqualTo(contact);
    }

    @Test
    void shouldAddRegisteredItem() {
        SubmissionRecord record = new SubmissionRecord();
        RegisteredItem item = new AdministeredItem();
        record.getRegisteredItems().add(item);
        assertThat(record.getRegisteredItems()).hasSize(1);
    }
}
