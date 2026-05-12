package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamespaceTest {

    @Test
    void shouldInitializeScopedIdentifiers() {
        Namespace ns = new Namespace();
        assertThat(ns.getScopedIdentifiers()).isEmpty();
    }

    @Test
    void shouldAddScopedIdentifier() {
        Namespace ns = new Namespace();
        ScopedIdentifier si = new ScopedIdentifier();
        ns.getScopedIdentifiers().add(si);
        assertThat(ns.getScopedIdentifiers()).hasSize(1);
    }

    @Test
    void shouldSetNamingAuthority() {
        Namespace ns = new Namespace();
        RegistrationAuthority auth = new RegistrationAuthority();
        ns.setNamingAuthority(auth);
        assertThat(ns.getNamingAuthority()).isEqualTo(auth);
    }

    @Test
    void shouldInheritItemBehaviour() {
        Namespace ns = new Namespace();
        assertThat(ns.getSlots()).isEmpty();
        assertThat(ns.getDesignations()).isEmpty();
        assertThat(ns.getDefinitions()).isEmpty();
    }
}
