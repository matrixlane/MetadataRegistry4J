package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.EmailAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void shouldSetFields() {
        Role role = new Role();
        role.setRoleName("Administrator");
        role.getMailAddress().setValue("admin@example.com");
        role.getEmailAddress().setValue("admin@example.com");
        role.getPhoneNumber().setValue("+86-138-0000-0000");

        assertThat(role.getRoleName()).isEqualTo("Administrator");
        assertThat(role.getMailAddress().getValue()).isEqualTo("admin@example.com");
        assertThat(role.getEmailAddress().getValue()).isEqualTo("admin@example.com");
        assertThat(role.getPhoneNumber().getValue()).isEqualTo("+86-138-0000-0000");
    }

    @Test
    void toStringShouldReturnRoleName() {
        Role role = new Role();
        role.setRoleName("User");
        assertThat(role.toString()).isEqualTo("User");
    }
}
