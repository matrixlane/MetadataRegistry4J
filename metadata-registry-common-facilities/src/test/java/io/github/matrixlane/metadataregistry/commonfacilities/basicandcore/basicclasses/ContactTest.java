package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContactTest {

    @Test
    void shouldCreateWithOrganization() {
        Organization org = new Organization();
        Contact c = new Contact(org);
        assertThat(c.getOrganization()).isEqualTo(org);
    }

    @Test
    void shouldSetIndividualAndRole() {
        Contact c = new Contact();
        Individual ind = new Individual();
        Role role = new Role();
        c.setIndividual(ind);
        c.setRole(role);
        assertThat(c.getIndividual()).isEqualTo(ind);
        assertThat(c.getRole()).isEqualTo(role);
    }
}
