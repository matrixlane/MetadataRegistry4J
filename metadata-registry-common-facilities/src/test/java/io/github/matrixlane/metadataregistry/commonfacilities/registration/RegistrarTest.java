package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrarTest {

    @Test
    void shouldConstructWithRegistrationAuthority() {
        RegistrationAuthority authority = new RegistrationAuthority();
        Registrar registrar = new Registrar(authority);
        assertThat(registrar.getRegistrationAuthority()).isEqualTo(authority);
    }

    @Test
    void shouldSetIdentifier() {
        Registrar registrar = new Registrar();
        registrar.setIdentifier("REG-001");
        assertThat(registrar.getIdentifier()).isEqualTo("REG-001");
    }

    @Test
    void shouldSetUser() {
        Registrar registrar = new Registrar();
        User user = new User();
        registrar.setUser(user);
        assertThat(registrar.getUser()).isEqualTo(user);
    }
}
