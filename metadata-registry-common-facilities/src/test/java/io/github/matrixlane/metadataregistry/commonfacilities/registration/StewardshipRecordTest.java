package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StewardshipRecordTest {

    @Test
    void shouldInitializeAdministeredItems() {
        StewardshipRecord record = new StewardshipRecord();
        assertThat(record.getAdministeredItems()).isEmpty();
    }

    @Test
    void shouldSetOrganization() {
        StewardshipRecord record = new StewardshipRecord();
        Organization org = new Organization();
        record.setOrganization(org);
        assertThat(record.getOrganization()).isEqualTo(org);
    }

    @Test
    void shouldSetContact() {
        StewardshipRecord record = new StewardshipRecord();
        Contact contact = new Contact();
        record.setContact(contact);
        assertThat(record.getContact()).isEqualTo(contact);
    }

    @Test
    void shouldAddAdministeredItem() {
        StewardshipRecord record = new StewardshipRecord();
        AdministeredItem item = new AdministeredItem();
        record.getAdministeredItems().add(item);
        assertThat(record.getAdministeredItems()).hasSize(1);
    }
}
