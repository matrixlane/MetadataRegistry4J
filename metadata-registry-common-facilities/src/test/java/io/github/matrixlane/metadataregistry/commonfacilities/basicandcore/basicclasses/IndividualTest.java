package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndividualTest {

    @Test
    void shouldSetFields() {
        Individual ind = new Individual();
        ind.setTitle("Dr.");
        ind.getName().setValue("Jane Doe");
        ind.getMailAddress().setValue("123 Main St");
        ind.getEmailAddress().add("jane@example.com");
        ind.getPhoneNumber().add(PhoneNumber.of("+86-138-0000-0000"));

        assertThat(ind.getTitle()).isEqualTo("Dr.");
        assertThat(ind.getName().getValue()).isEqualTo("Jane Doe");
        assertThat(ind.getMailAddress().getValue()).isEqualTo("123 Main St");
        assertThat(ind.getEmailAddress()).hasSize(1);
        assertThat(ind.getPhoneNumber()).hasSize(1);
    }

    @Test
    void toStringShouldReturnNameValue() {
        Individual ind = new Individual();
        ind.getName().setValue("Alice");
        assertThat(ind.toString()).isEqualTo("Alice");
    }

    @Test
    void toStringWithNullNameShouldReturnNull() {
        Individual ind = new Individual();
        ind.setName(null);
        assertThat(ind.toString()).isNull();
    }
}
