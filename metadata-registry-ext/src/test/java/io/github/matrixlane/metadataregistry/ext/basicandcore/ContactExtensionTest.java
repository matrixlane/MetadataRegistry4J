package io.github.matrixlane.metadataregistry.ext.basicandcore;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ContactExtension：账号绑定卫星实体（值键，contact_id 即 PK）。 */
class ContactExtensionTest {

    @Test
    void shouldCreateWithValueKeys() {
        UUID contactId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        ContactExtension extension = new ContactExtension(contactId, accountId);
        assertThat(extension.getId()).isEqualTo(contactId);
        assertThat(extension.getAccountId()).isEqualTo(accountId);
    }

    @Test
    void noArgConstructorLeavesFieldsNull() {
        ContactExtension extension = new ContactExtension();
        assertThat(extension.getId()).isNull();
        assertThat(extension.getAccountId()).isNull();
    }
}
