package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void shouldSetFields() {
        User user = new User();
        user.setObjectId("obj-123");
        user.setTenautId("tenant-456");
        user.setName("John");
        user.setEmail("john@example.com");

        assertThat(user.getObjectId()).isEqualTo("obj-123");
        assertThat(user.getTenautId()).isEqualTo("tenant-456");
        assertThat(user.getName()).isEqualTo("John");
        assertThat(user.getEmail()).isEqualTo("john@example.com");
    }
}
