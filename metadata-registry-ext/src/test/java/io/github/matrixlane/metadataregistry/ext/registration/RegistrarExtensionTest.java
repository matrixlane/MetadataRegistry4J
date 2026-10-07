package io.github.matrixlane.metadataregistry.ext.registration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RegistrarExtension：默认注册人员标志卫星实体（值键，registrar_id 即 PK）。 */
class RegistrarExtensionTest {

    @Test
    void shouldCreateWithValueKeys() {
        UUID registrarId = UUID.randomUUID();
        RegistrarExtension extension = new RegistrarExtension(registrarId, true);
        assertThat(extension.getId()).isEqualTo(registrarId);
        assertThat(extension.isDefault()).isTrue();
    }

    @Test
    void noArgConstructorDefaultsToNotDefault() {
        RegistrarExtension extension = new RegistrarExtension();
        assertThat(extension.getId()).isNull();
        assertThat(extension.isDefault()).isFalse();
    }
}
