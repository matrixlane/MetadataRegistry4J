package io.github.matrixlane.metadataregistry.ext.basicandcore;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContactAccountBindingTest {

    @Test
    void shouldCreateWithValueKeys() {
        UUID contactId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ContactAccountBinding binding = new ContactAccountBinding(contactId, accountId);
        assertThat(binding.getContactId()).isEqualTo(contactId);
        assertThat(binding.getAccountId()).isEqualTo(accountId);
        assertThat(binding.getId()).isNull();
    }
}
