package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.EmailAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationTest {

    @Test
    void shouldSetFields() {
        Organization org = new Organization();
        org.getName().add(Sign.of("Acme Corp"));
        org.getMailAddress().setValue("123 Business Rd");
        org.getEmailAddress().add(EmailAddress.of("info@acme.com"));
        org.getPhoneNumber().add(PhoneNumber.of("+1-555-0000"));
        org.setWebAddress("https://acme.com");

        assertThat(org.getName()).hasSize(1);
        assertThat(org.getMailAddress().getValue()).isEqualTo("123 Business Rd");
        assertThat(org.getEmailAddress()).hasSize(1);
        assertThat(org.getPhoneNumber()).hasSize(1);
        assertThat(org.getWebAddress()).isEqualTo("https://acme.com");
    }

    @Test
    void toStringShouldReturnFirstName() {
        Organization org = new Organization();
        org.getName().add(Sign.of("Acme"));
        assertThat(org.toString()).isEqualTo("Acme");
    }

    @Test
    void toStringWithEmptyNameShouldReturnNull() {
        Organization org = new Organization();
        assertThat(org.toString()).isNull();
    }
}
